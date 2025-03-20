package com.example.audioapp.services;

import com.example.audioapp.entity.GlobalHistory;
import com.example.audioapp.utils.BaselineCalculator;
import com.example.audioapp.utils.BasicReplyHelper;
import com.example.audioapp.utils.FaceDetectorHelper;
import com.example.audioapp.utils.ModelLoader;
import com.example.audioapp.R;
import com.example.audioapp.entity.CapturedData;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.HardwareBuffer;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.example.audioapp.utils.ChatGptHelper;
import com.example.audioapp.utils.PreferenceHelper;
import com.example.audioapp.utils.YuvToRgbConverter;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * EmotionMonitoringService 用于实时采集照片、调用 torch 模型推理出 V-A 值，
 * 并在检测到负面情绪异常时保存最近 30 张照片及其记录。
 */
public class EmotionMonitoringService extends Service {
    private static final String TAG = "EmotionMonitoringService";
    private static final int NOTIFICATION_ID = 2;

    // 定义一些阈值和最小采样数量
    private static final int WINDOW_SAMPLES_NUM = 5; // 窗口长度5条数据
    private static final int HISTORY_LEAST_SAMPLES_NUM = 50; // 至少300个历史数据再记录
    private static final int MIN_RECENT_SAMPLES = 3;        // 至少需要3个采样点
    private static final float AROUSAL_STD_THRESHOLD = 0.12f; // 激活度标准差阈值
    private static final float VALENCE_STD_THRESHOLD = 0.12f; // 情绪价值标准差阈值

    private static final float AROUSAL_Z_SCORE_THRESHOLD = 1.3f; // 激活度标准差阈值
    private static final float VALENCE_Z_SCORE_THRESHOLD = 1.3f; // 情绪价值标准差阈值
    private static final float NEGATIVE_RATIO_THRESHOLD = 0.3f; // 窗口中负面采样占比阈值0.3
    private static final float ANGER_VALENCE_THRESHOLD = -0.25f;
    private static final float ANGER_AROUSAL_THRESHOLD = 0.3f;

    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private ScheduledExecutorService scheduledExecutor;
    // 用于保存照片和 V-A 记录的缓冲区
    public static final List<CapturedData> captureBuffer = new LinkedList<>();

    // 请确保你已经加载好了 PyTorch 模型，这里假设 module 是一个全局变量或通过其他方式传入
    // private Module module;

    // 用于标记是否已经触发异常信号
    private boolean abnormalTriggered = false;
    // 定义全局变量
    private long lastAbnormalTime = 0;
    private static final long ABNORMAL_COOLDOWN_MS = 30_000; // 冷却时间，例如60秒

    // 数据结构：保存一张照片和对应的 V-A 值记录

    // 以下为屏幕截图相关字段
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    private int gameType;

    private ModelLoader modelLoader;
    // 全局变量存储最新截图
    private volatile Bitmap latestScreenBitmap = null;
    private long lastScreenshotTimestamp = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化屏幕参数
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
        // 初始化定时任务执行器
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

        // 创建通知渠道（Android 8.0及以上）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "emotion_monitoring_channel",
                    "Emotion Monitoring Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            // ChatGPT 回复通知渠道（高优先级，支持弹出和声音）
            NotificationChannel replyChannel = new NotificationChannel(
                    "chatgpt_reply_channel",
                    "ChatGPT Replies",
                    NotificationManager.IMPORTANCE_HIGH
            );
            replyChannel.setDescription("显示 ChatGPT 的完整回复内容");
            replyChannel.enableVibration(true);

            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                manager.createNotificationChannel(replyChannel);
            }
        }
        modelLoader = new ModelLoader(getAssets());


    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: Service is starting");
        // 启动前台服务，避免在后台被杀
        startForeground(NOTIFICATION_ID, createNotification());

        // 如果 Intent 中传入了 MediaProjection 权限信息，则初始化屏幕捕捉
        if (intent != null && intent.hasExtra("resultCode") && intent.hasExtra("resultData")) {
            int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
            Intent resultData = intent.getParcelableExtra("resultData");
            initMediaProjection(resultCode, resultData);
        } else {
            Log.w(TAG, "onStartCommand: No MediaProjection extras, screenshot capture disabled");
        }
        //



        // 启动摄像头
        startCamera();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 保存 globalHistory 到 CSV 文件
        File csvFile = new File(getExternalFilesDir(Environment.DIRECTORY_ALARMS + "/GlobalHistory"), "av_record_globalHistory.csv");
        GlobalHistory.saveToCSV(csvFile);
        Log.d(TAG, "Global history saved to " + csvFile.getAbsolutePath());
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // 本服务只作为启动式服务，不支持绑定
        return null;
    }

    // 创建服务通知
    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "emotion_monitoring_channel")
                .setContentTitle("情绪监测中")
                .setContentText("正在通过摄像头采集数据并分析情绪...")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setOngoing(true);
        return builder.build();
    }

    // 初始化并启动摄像头：这里使用 ImageCapture 用例采集照片
    private void startCamera() {
        Log.d(TAG, "startCamera: initializing camera");

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                // 预览（如果需要可显示预览）
                //Preview preview = new Preview.Builder().build();

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                // 使用前置摄像头（或根据需求选择后置）
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                // 绑定用例到一个简单的 LifecycleOwner
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(new LifecycleOwner() {
                    @NonNull
                    @Override
                    public Lifecycle getLifecycle() {
                        // 简单实现一个始终处于 STARTED 状态的 Lifecycle
                        return new Lifecycle() {
                            @Override
                            public void addObserver(@NonNull LifecycleObserver observer) { }
                            @Override
                            public void removeObserver(@NonNull LifecycleObserver observer) { }
                            @NonNull
                            @Override
                            public State getCurrentState() {
                                return State.STARTED;
                            }
                        };
                    }
                }, cameraSelector, imageCapture);
                Log.d(TAG, "startCamera: Camera initialized successfully");
                // 摄像头初始化完成后，再启动定时任务，每隔1秒采集一张照片

                // 保存路径：例如保存在应用外部存储的文件夹中
//                String baseDir = getExternalFilesDir(Environment.DIRECTORY_ALARMS)+"/GlobalHistory";
//                File dir = new File(baseDir);
//                if (dir.exists()) {
//                    // 同时生成一个文本文件记录 V-A 值
//                    String csvFileName = "av_record_globalHistory.csv";
//                    File csvFile = new File(dir, csvFileName);
//                    GlobalHistory.loadFromCSV(csvFile);
//                    Log.d(TAG, "onStartCommand: GlobalHistory loaded");
//                }

                scheduledExecutor.scheduleWithFixedDelay(() -> captureAndProcess(), 0,  1000, TimeUnit.MILLISECONDS);

            } catch (Exception e) {
                Log.e(TAG, "启动摄像头失败: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // 初始化 MediaProjection 以捕获屏幕截图
    private void initMediaProjection(int resultCode, Intent resultData) {
        MediaProjectionManager projectionManager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData);
            if (mediaProjection != null) {
                imageReader = ImageReader.newInstance(screenWidth, screenHeight, ImageFormat.PRIVATE, 2);
                virtualDisplay = mediaProjection.createVirtualDisplay("ScreenCapture",
                        screenWidth, screenHeight, screenDensity,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.getSurface(), null, null);
                // 设置监听器更新全局最新截图
//                imageReader.setOnImageAvailableListener(reader -> {
//                    Image image = reader.acquireLatestImage();
//                    if (image != null) {
//                        long currentTime = System.currentTimeMillis();
//                        // 如果上次更新截图距离当前不足1秒，则直接关闭image
//                        if (currentTime - lastScreenshotTimestamp < 1000) {
//                            image.close();
//                            return;
//                        }
//                        lastScreenshotTimestamp = currentTime;
//
//                        // 将 image 转为 Bitmap
//                        Bitmap newBitmap = null;
//                        HardwareBuffer buffer = image.getHardwareBuffer();
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && buffer != null) {
//                            newBitmap = Bitmap.wrapHardwareBuffer(buffer, null);
//                        }
//                        image.close();
//                        if (newBitmap != null) {
//                            latestScreenBitmap = newBitmap;
//                            Log.d(TAG, "New screenshot updated at " + currentTime);
//                        }
//                    }
//                }, new Handler(Looper.getMainLooper()));

                Log.d(TAG, "initMediaProjection: MediaProjection initialized successfully");
            } else {
                Log.e(TAG, "initMediaProjection: MediaProjection is null");
            }
        } else {
            Log.e(TAG, "initMediaProjection: Cannot get MediaProjectionManager");
        }
    }

    // 定时采集照片并处理：拍照、模型推理、保存记录、检测异常
    private void captureAndProcess() {
        if (imageCapture == null) {
            Log.e(TAG, "captureAndProcess: imageCapture is null");
            return;
        }
        Bitmap cameraBitmap;
        gameType = getApplicationContext().getSharedPreferences("emo_preferences", Context.MODE_PRIVATE)
                .getInt("game_type", 1);
        imageCapture.takePicture(ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        // 将 ImageProxy 转换为 Bitmap
                        Bitmap cameraBitmap = imageProxyToBitmap(image);
                        image.close();

                        if (cameraBitmap == null) {
                            Log.e(TAG, "captureAndProcess: cameraBitmap conversion failed");
                            return;
                        }
                        FaceDetectorHelper.cropFaceFromBitmap(getApplicationContext(), cameraBitmap, new FaceDetectorHelper.FaceDetectionCallback() {
                            @Override
                            public void onFaceDetected(@Nullable Bitmap faceBitmap) {
                                if (faceBitmap == null) {
                                    // 没有检测到人脸，则不进行模型推理
                                    Log.d(TAG, "No face detected, skipping model inference.");
                                } else {
                                    // 检测到人脸，继续后续处理，比如调用模型进行推理
                                    float[] avValues = modelLoader.runModel(faceBitmap);
                                    long timestamp = System.currentTimeMillis();
                                    // 存储结果到缓冲区，或者执行其他操作
                                    Log.d(TAG, "Face detected, proceeding with model inference.");
                                    // 使用全局最新截图，不再重新调用 acquireLatestImage()
                                    captureScreenManually();//
                                    Bitmap screenBitmap = latestScreenBitmap;
                                    if (screenBitmap != null) {
                                        screenBitmap = screenBitmap.copy(screenBitmap.getConfig(), false);
                                    } else {
                                        Log.d(TAG, "onCaptureSuccess: latestScreenBitmap is null");
                                    }
                                    // 将数据存入缓冲区，保证缓冲区最多保存最近 BUFFER_SIZE 条记录
                                    synchronized (captureBuffer) {
                                        if (captureBuffer.size() >= WINDOW_SAMPLES_NUM) {
                                            captureBuffer.remove(0);
                                        }
                                        captureBuffer.add(new CapturedData(faceBitmap,screenBitmap ,avValues, timestamp));
                                    }
                                    Log.d(TAG, "captureAndProcess: Captured image at " + timestamp + " with AV: " +
                                            avValues[0] + ", " + avValues[1]);

                                    // 同步更新到全局历史
                                    GlobalHistory.updateGlobalHistory(new CapturedData(avValues, timestamp));

                                    // 检测是否存在异常（例如：v）

                                    if (isNegativeAbnormal()) {
                                        long currentTime = System.currentTimeMillis();
                                        if (currentTime - lastAbnormalTime >= ABNORMAL_COOLDOWN_MS) {
                                            lastAbnormalTime = currentTime;
                                            Log.d(TAG, "captureAndProcess: Negative emotion detected!");
                                            //Toast.makeText(getApplicationContext(), "检测到负面情绪异常", Toast.LENGTH_SHORT).show();
                                            saveAbnormalData();
                                            String replyStr;

                                            File csvFile = new File(getExternalFilesDir(Environment.DIRECTORY_ALARMS + "/GlobalHistory"), "av_record_globalHistory.csv");
                                            GlobalHistory.saveToCSV(csvFile);

                                            // ChatGpt
                                            if (PreferenceHelper.isUseBasicReply(getApplicationContext())) {
                                                // 使用基础回复
                                                String basicReply = BasicReplyHelper.getRandomReply();
                                                new Handler(Looper.getMainLooper()).post(() -> {
                                                    Toast.makeText(getApplicationContext(), basicReply, Toast.LENGTH_LONG).show();
                                                    Log.d(TAG, "onSuccess: BasicReply: " + basicReply);
                                                });
                                                // 记录日志到文件等操作也可以添加在这里
                                            }else {
                                                // 使用 GPT 模型回复，示例调用 getInterventionResponse
                                                ChatGptHelper chatGptHelper = new ChatGptHelper();
                                                chatGptHelper.getInterventionResponse(
                                                        captureBuffer,
                                                        gameType,
                                                        new ChatGptHelper.ChatGptCallback() {
                                                            @Override
                                                            public void onSuccess(String reply) {
                                                                // 记录成功回复
                                                                long timestamp = System.currentTimeMillis();
                                                                logReplyToFile(timestamp, reply);
                                                                // 在主线程中更新 UI，可使用 Handler 切换
                                                                new Handler(Looper.getMainLooper()).post(() -> {
                                                                    Toast toast = Toast.makeText(getApplicationContext(), reply, Toast.LENGTH_LONG);
                                                                    toast.show();
                                                                    Log.d(TAG, "onSuccess: "+reply);
                                                                    // 额外延长显示时间
                                                                    new Handler(Looper.getMainLooper()).postDelayed(toast::show, 1000); // 额外延长 3.5 秒
                                                                });
                                                            }
                                                            @Override
                                                            public void onFailure(String error) {
                                                                // 记录失败信息
                                                                long timestamp = System.currentTimeMillis();
                                                                logReplyToFile(timestamp, "Failed to get response: " + error);
                                                                new Handler(Looper.getMainLooper()).post(() -> {
                                                                    //showCustomToast(error,5000);
                                                                    //Toast.makeText(getApplicationContext(), "Failed to get response: " + error, Toast.LENGTH_SHORT).show();
                                                                    Log.e(TAG, "onFailure: Failed to get response:" + error );
                                                                });
                                                            }
                                                        }
                                                );
                                            }

                                        }
                                    }

                                }
                            }
                        });

                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "captureAndProcess: Error capturing image: " + exception.getMessage());
                    }
                });

    }



    private boolean isNegativeAbnormal() {
        if (EmotionMonitoringService.captureBuffer.size() < WINDOW_SAMPLES_NUM) {
            // 数据不足
            return false;
        }

        // Step 1: 获取全局历史
        List<CapturedData> globalHistory = GlobalHistory.getGlobalVAList();
        if(globalHistory.size() < HISTORY_LEAST_SAMPLES_NUM){
            return false;
        }
        // Step 2: 计算基线
        BaselineCalculator.BaselineStats baseline = BaselineCalculator.computeBaseline(globalHistory);

        int total = EmotionMonitoringService.captureBuffer.size();
        int negativeCount = 0;
        int farFromBaselineCount = 0;


        // Step 3: 判断短期窗口是否偏离基线
        float sumArousal = 0, sumValence = 0;
        for (CapturedData data : EmotionMonitoringService.captureBuffer) {
            float arousal = data.avValues[0];  // 唤醒度
            float valence = data.avValues[1];  // 愉悦程度

            // 定义负面情绪判断（这里以愤怒为例，你可以扩展更多情绪的判断）
            boolean isAngry = (valence < ANGER_VALENCE_THRESHOLD && arousal > ANGER_AROUSAL_THRESHOLD);
            boolean isSad = (valence < -0.35 && arousal < -0.1);

            boolean isNegative = isAngry|| isSad;
            if (isNegative) {
                negativeCount++;
            }

            // 方差计算
            // Step 3.1 判断与基线的差异（Z-score 或差值）
            float zArousal = (arousal - baseline.avgArousal) / (baseline.stdArousal + 1e-5f);
            float zValence = (valence - baseline.avgValence) / (baseline.stdValence + 1e-5f);
            if((Math.abs(zArousal) > AROUSAL_Z_SCORE_THRESHOLD || Math.abs(zValence) > VALENCE_Z_SCORE_THRESHOLD)){
                farFromBaselineCount++;
            }
        }



        // 如果 arousal、valence 在基线的 1~2 个标准差之外，说明有显著波动



        // Step 3.2 根据情绪圆环阈值判断是否属于负面区域
        // 例如：愤怒 Angry => valence < -0.2, arousal > 0.6


        // 方案2：判断整体窗口中负面采样比例是否超过阈值
        boolean negativeRatioCondition = ((float) negativeCount / total) >= NEGATIVE_RATIO_THRESHOLD;
        boolean isFarFromBaseline = ((float) farFromBaselineCount / total) >= NEGATIVE_RATIO_THRESHOLD;
        // 你可以继续定义其他情绪阈值，比如烦躁、悲伤等
        // ...
        // 方案3，如果是在笑，就舍弃
        boolean isHappy = EmotionMonitoringService.captureBuffer.get(3).avValues[1] > 0.15 || EmotionMonitoringService.captureBuffer.get(4).avValues[1] > 0.15;

        // 最终逻辑：若显著偏离基线 并且 落入负面情绪区域，就认为异常
        return (isFarFromBaseline && negativeRatioCondition) && !isHappy;
    }

    @SuppressLint("SimpleDateFormat")
    private void logReplyToFile(long timestamp, String message) {
        File logFile = new File(getExternalFilesDir(Environment.DIRECTORY_ALARMS + "/GlobalHistory"), "reply_logs.txt");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(timestamp)) + " - " + message);
            writer.newLine();
        } catch (IOException e) {
            Log.e(TAG, "Error writing to log file", e);
        }
    }

    // 将缓冲区内（最近30条）的照片和 V-A 值记录保存下来
    private void saveAbnormalData() {
        // 保存路径：例如保存在应用外部存储的文件夹中
        String baseDir = getExternalFilesDir(Environment.DIRECTORY_ALARMS) + "/WindowCapture/Abnormal_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File dir = new File(baseDir);
        Log.d(TAG, "saveAbnormalData:dir directory " + baseDir);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "saveAbnormalData: Failed to create directory " + baseDir);
            return;
        }

        // 同时生成一个文本文件记录 V-A 值
        String csvFileName = "av_record_captureBuffer.csv";
        File csvFile = new File(dir, csvFileName);

        try (FileOutputStream fos = new FileOutputStream(csvFile)) {
            // 写入 CSV 表头
            String header = "Timestamp,Arousal,Valence\n";
            fos.write(header.getBytes());

            synchronized (captureBuffer) {
                for (CapturedData data : captureBuffer) {
                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date(data.timestamp));
                    String camFileName = "CAM_" + timestamp + ".jpg";
                    String scrFileName = "NA";
                    File camFile = new File(dir, camFileName);
                    try (FileOutputStream camOut = new FileOutputStream(camFile)) {
                        data.cameraBitmap.compress(Bitmap.CompressFormat.JPEG, 40, camOut);
                    }
                    // 如果屏幕截图存在，也保存
                    if (data.screenBitmap != null) {
                        scrFileName = "SCR_" + timestamp + ".jpg";
                        File scrFile = new File(dir, scrFileName);
                        try (FileOutputStream scrOut = new FileOutputStream(scrFile)) {
                            data.screenBitmap.compress(Bitmap.CompressFormat.JPEG, 40, scrOut);
                        }
                    }
                    // 生成 CSV 行：摄像头文件名,屏幕截图文件名,,Arousal,Valence
                    String line = timestamp + "," + data.avValues[0] + "," + data.avValues[1] + "\n";
                    fos.write(line.getBytes());
                }
            }


            fos.flush();
            Log.d(TAG, "saveAbnormalData: Abnormal data saved successfully to " + baseDir);
        } catch (IOException e) {
            Log.e(TAG, "保存异常数据失败: " + e.getMessage());
        }
    }



    // 将 ImageProxy 转换为 Bitmap 的工具方法（简化版）
    private Bitmap imageProxyToBitmap(ImageProxy image) {
        if (image.getFormat() == ImageFormat.JPEG) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } else if (image.getFormat() == ImageFormat.YUV_420_888) {
            // 如果是 YUV 格式，使用 YuvToRgbConverter 进行转换
            YuvToRgbConverter converter = new YuvToRgbConverter(getApplicationContext());
            Bitmap bitmap = Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            converter.yuvToRgb(image, bitmap);
            return bitmap;
        } else {
            Log.e(TAG, "Unsupported image format: " + image.getFormat());
            return null;
        }
    }

    private void captureScreenManually() {
        if (imageReader == null) {
            Log.d(TAG, "captureScreenManually: imageReader is null");
            return;
        }
        Log.d(TAG, "captureScreenManually: Attempting to acquire latest image...");
        Image image = imageReader.acquireLatestImage();
        if (image == null) {
            Log.d(TAG, "captureScreenManually: acquireLatestImage returned null");
            return;
        }
        Log.d(TAG, "captureScreenManually: Acquired an image");

        Bitmap newBitmap = null;
        HardwareBuffer buffer = image.getHardwareBuffer();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (buffer != null) {
                newBitmap = Bitmap.wrapHardwareBuffer(buffer, null);
                if (newBitmap != null) {
                    Log.d(TAG, "captureScreenManually: Bitmap created from HardwareBuffer");
                } else {
                    Log.d(TAG, "captureScreenManually: Bitmap.wrapHardwareBuffer returned null");
                }
            } else {
                Log.d(TAG, "captureScreenManually: HardwareBuffer is null");
            }
        } else {
            Log.d(TAG, "captureScreenManually: SDK version < Q, cannot use HardwareBuffer method");
        }
        image.close();

        if (newBitmap != null) {
            latestScreenBitmap = newBitmap;
            Log.d(TAG, "Manually captured new screenshot");
        } else {
            Log.d(TAG, "captureScreenManually: newBitmap is null after processing");
        }
    }

    private void showCustomToast(final String message, final int displayDurationMillis) {
        // 获取 LayoutInflater
        LayoutInflater inflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        // 加载自定义布局
        final View layout = inflater.inflate(R.layout.toast_layout, null);

        // 设置文本内容和样式
        TextView toastText = layout.findViewById(R.id.toast_text);
        toastText.setText(message);
        // 若需要进一步调整字体样式可在这里修改

        // 设置图标（如果需要）
//        ImageView toastIcon = layout.findViewById(R.id.toast_icon);
//        toastIcon.setImageResource(R.drawable.your_custom_icon); // 替换为你想用的图标

        // 创建 Toast 对象
        final Toast toast = new Toast(getApplicationContext());
        toast.setView(layout);
        toast.setGravity(Gravity.CENTER, 0, 0);

        // 默认 Toast.LENGTH_LONG 大约显示 3500ms
        final int toastDuration = 5000;
        final long startTime = System.currentTimeMillis();

        final Handler handler = new Handler(Looper.getMainLooper());
        Runnable toastRunnable = new Runnable() {
            @Override
            public void run() {
                toast.show();
                // 如果总显示时间未到，则再次调度显示
                if (System.currentTimeMillis() - startTime < displayDurationMillis) {
                    handler.postDelayed(this, toastDuration);
                }
            }
        };

        handler.post(toastRunnable);
    }





}

