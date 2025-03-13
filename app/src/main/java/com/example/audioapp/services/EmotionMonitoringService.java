package com.example.audioapp.services;

import com.example.audioapp.utils.FaceDetectorHelper;
import com.example.audioapp.utils.ModelLoader;
import com.example.audioapp.R;
import com.example.audioapp.entity.CapturedData;

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
import com.example.audioapp.utils.YuvToRgbConverter;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    private static final int BUFFER_SIZE = 30;  // 保存最近30张照片

    // 定义一些阈值和最小采样数量
    private static final int MIN_SAMPLES = 20; // 至少20个再记录
    private static final long WINDOW_DURATION_MS = 10_000; // 10秒
    private static final int MIN_RECENT_SAMPLES = 3;        // 至少需要3个采样点
    private static final float AROUSAL_STD_THRESHOLD = 0.12f; // 激活度标准差阈值
    private static final float VALENCE_STD_THRESHOLD = 0.12f; // 情绪价值标准差阈值

    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private ScheduledExecutorService scheduledExecutor;
    // 用于保存照片和 V-A 记录的缓冲区
    private final List<CapturedData> captureBuffer = new LinkedList<>();

    // 请确保你已经加载好了 PyTorch 模型，这里假设 module 是一个全局变量或通过其他方式传入
    // private Module module;

    // 用于标记是否已经触发异常信号
    private boolean abnormalTriggered = false;
    // 定义全局变量
    private long lastAbnormalTime = 0;
    private static final long ABNORMAL_COOLDOWN_MS = 60_000; // 冷却时间，例如60秒

    // 数据结构：保存一张照片和对应的 V-A 值记录

    // 以下为屏幕截图相关字段
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    private ModelLoader modelLoader;
    // 全局变量存储最新截图
    private volatile Bitmap latestScreenBitmap = null;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: EmotionMonitoringService is created");
        Toast.makeText(this, "Service onCreate", Toast.LENGTH_SHORT).show();
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

        // 启动摄像头
        startCamera();

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Service is being destroyed");
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
                imageReader.setOnImageAvailableListener(reader -> {
                    Image image = reader.acquireLatestImage();
                    if (image != null) {
                        // 将 image 转为 Bitmap
                        Bitmap newBitmap = null;
                        HardwareBuffer buffer = image.getHardwareBuffer();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && buffer != null) {
                            newBitmap = Bitmap.wrapHardwareBuffer(buffer, null);
                        }
                        image.close();
                        if (newBitmap != null) {
                            latestScreenBitmap = newBitmap;

                            Log.d(TAG, "New screenshot updated");
                        }
                    }
                }, new Handler(Looper.getMainLooper()));

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
                                    Bitmap screenBitmap = latestScreenBitmap;
                                    if (screenBitmap == null) {
                                        Log.d(TAG, "onCaptureSuccess: latestScreenBitmap is null");
                                    }
                                    // 将数据存入缓冲区，保证缓冲区最多保存最近 BUFFER_SIZE 条记录
                                    synchronized (captureBuffer) {
                                        if (captureBuffer.size() >= BUFFER_SIZE) {
                                            captureBuffer.remove(0);
                                        }
                                        captureBuffer.add(new CapturedData(faceBitmap,screenBitmap ,avValues, timestamp));
                                    }
                                    Log.d(TAG, "captureAndProcess: Captured image at " + timestamp + " with AV: " +
                                            avValues[0] + ", " + avValues[1]);
                                    // 检测是否存在异常（例如：v）

                                    if (isNegativeAbnormal(captureBuffer)) {
                                        long currentTime = System.currentTimeMillis();
                                        if (currentTime - lastAbnormalTime >= ABNORMAL_COOLDOWN_MS) {
                                            lastAbnormalTime = currentTime;
                                            Log.d(TAG, "captureAndProcess: Negative emotion detected!");
                                            saveAbnormalData();
                                            Toast.makeText(getApplicationContext(), "检测到负面情绪异常", Toast.LENGTH_SHORT).show();
                                            // 在检测异常的地方调用（例如在 captureAndProcess() 中）：
                                            String abnormalInfo = "Valence: " + avValues[0] + ", Arousal: " + avValues[1] + " at timestamp " + timestamp;
                                            // ChatGpt
                                            ChatGptHelper chatGptHelper = new ChatGptHelper();
                                            chatGptHelper.getInterventionResponse(
                                                    captureBuffer.get(captureBuffer.size()-1),
                                                    new ChatGptHelper.ChatGptCallback() {
                                                        @Override
                                                        public void onSuccess(String reply) {
                                                            // 在主线程中更新 UI，可使用 Handler 切换
                                                            new Handler(Looper.getMainLooper()).post(() -> {
                                                                Toast.makeText(getApplicationContext(),  reply, Toast.LENGTH_LONG).show();
                                                                Log.d(TAG, "onSuccess: ChatGPT: " + reply);
                                                                // 构建通知
                                                                NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), "chatgpt_reply_channel")
                                                                        .setContentTitle("ChatGPT 回复")
                                                                        .setContentText(reply)
                                                                        .setSmallIcon(R.drawable.ic_launcher_background)
                                                                        .setStyle(new NotificationCompat.BigTextStyle().bigText(reply)) // 展开显示完整内容
                                                                        .setPriority(NotificationCompat.PRIORITY_MAX) // 最高优先级
                                                                        .setAutoCancel(true); // 点击后自动消失

                                                                // 发送通知
                                                                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                                                                if (manager != null) {
                                                                    int notificationId = (int) System.currentTimeMillis(); // 生成唯一 ID
                                                                    manager.notify(notificationId, builder.build());
                                                                }

//                                                    Log.d(TAG, "onSuccess: "+"ChatGPT: " + reply);
                                                            });
                                                        }
                                                        @Override
                                                        public void onFailure(String error) {
                                                            new Handler(Looper.getMainLooper()).post(() -> {
                                                                Toast.makeText(getApplicationContext(), "Failed to get response: " + error, Toast.LENGTH_SHORT).show();
                                                                Log.e(TAG, "onFailure: Failed to get response:" + error );
                                                            });
                                                        }
                                                    }
                                            );
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

    // 捕获屏幕截图：使用 ImageReader 获取最新的 Image 并转换为 Bitmap
    private Bitmap captureScreenShot() {
        if (imageReader == null) {
            Log.e(TAG, "captureScreenShot: imageReader is null");
            return null;
        }

        Image image = imageReader.acquireLatestImage();
        if (image == null) {
            Log.e(TAG, "captureScreenShot: No Screen Image available");
            return null;
        }
        // 获取 HardwareBuffer
        HardwareBuffer buffer = image.getHardwareBuffer();
        // 使用 HardwareBuffer 创建 Bitmap（需要 API 26+）
        Bitmap bitmap = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bitmap = Bitmap.wrapHardwareBuffer(buffer, null);
        }
        image.close();
        return bitmap;
    }


    // 判断 V-A 值是否异常，此处简单判断 valence 是否低于阈值
    /**
     * 判断过去10秒内情绪数据的波动率是否超过预设阈值，
     * 如果任一指标（arousal或valence）的标准差超过阈值，则认为情绪出现急剧波动，触发异常。
     */
    private boolean isNegativeAbnormal(List<CapturedData> captureBuffer) {
        if (captureBuffer.size() < MIN_SAMPLES) {
            // 数据不足，不进行判断
            return false;
        }
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - WINDOW_DURATION_MS;

        // 筛选出过去10秒内的数据
        List<CapturedData> recentData = new ArrayList<>();
        for (CapturedData data : captureBuffer) {
            if (data.timestamp >= windowStart) {
                recentData.add(data);
            }
        }

        // 如果采样数量不足，则不做判断
        if (recentData.size() < MIN_RECENT_SAMPLES) {
            return false;
        }

        int n = recentData.size();
        float sumArousal = 0, sumValence = 0;
        for (CapturedData data : recentData) {
            sumArousal += data.avValues[0];  // 索引0: arousal
            sumValence += data.avValues[1];   // 索引1: valence
        }
        float meanArousal = sumArousal / n;
        float meanValence = sumValence / n;

        // 计算标准差
        float sumSqDiffArousal = 0, sumSqDiffValence = 0;
        for (CapturedData data : recentData) {
            float diffArousal = data.avValues[0] - meanArousal;
            float diffValence = data.avValues[1] - meanValence;
            sumSqDiffArousal += diffArousal * diffArousal;
            sumSqDiffValence += diffValence * diffValence;
        }
        float stdArousal = (float) Math.sqrt(sumSqDiffArousal / n);
        float stdValence = (float) Math.sqrt(sumSqDiffValence / n);

        // 调试输出
        Log.d("AbnormalCheck", "Recent samples: " + n +
                ", stdArousal: " + stdArousal + ", stdValence: " + stdValence);

        // 如果任一指标的标准差超过设定阈值，则认为情绪波动剧烈
        if (stdArousal > AROUSAL_STD_THRESHOLD || stdValence > VALENCE_STD_THRESHOLD) {
            return true;
        }
        return false;
    }

    // 将缓冲区内（最近30条）的照片和 V-A 值记录保存下来
    private void saveAbnormalData() {
        // 保存路径：例如保存在应用外部存储的文件夹中
        String baseDir = getExternalFilesDir(Environment.DIRECTORY_ALARMS) + "/EmotionAbnormal_" +
                new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File dir = new File(baseDir);
        Log.d(TAG, "saveAbnormalData:dir directory " + baseDir);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "saveAbnormalData: Failed to create directory " + baseDir);
            return;
        }

        // 同时生成一个文本文件记录 V-A 值
        String csvFileName = "av_record.csv";
        File csvFile = new File(dir, csvFileName);

        try (FileOutputStream fos = new FileOutputStream(csvFile)) {
            // 写入 CSV 表头
            String header = "CameraImage,ScreenImage,Arousal,Valence\n";
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
                    // 生成 CSV 行：摄像头文件名,屏幕截图文件名,Valence,Arousal
                    String line = camFileName + "," + scrFileName + "," + data.avValues[0] + "," + data.avValues[1] + "\n";
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


}

