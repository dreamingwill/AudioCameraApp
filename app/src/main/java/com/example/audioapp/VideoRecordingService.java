package com.example.audioapp;
import static androidx.camera.core.CameraXThreads.TAG;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.example.audioapp.utils.FileUtil;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoRecordingService extends Service {
    private static final String TAG = "VideoRecordingService";
    private static final int NOTIFICATION_ID = 1;

    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private PreviewView viewFinder;

    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public VideoRecordingService getService() {
            return VideoRecordingService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: VideoRecordingService is created");
        // 创建通知渠道（Android 8.0及以上）
        cameraExecutor = Executors.newSingleThreadExecutor();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "video_recording_channel",
                    "Video Recording Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // 检查权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            // 如果没有权限，发送广播或启动 Activity 请求权限
            Intent permissionIntent = new Intent(this, MainActivity.class);
            permissionIntent.putExtra("request_permissions", true);
            permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);  // 启动新的任务
            startActivity(permissionIntent);
            return START_NOT_STICKY;
        }
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification());

        startCamera();  // 启动摄像头,启动视频和音频录制也放进去了




        // 返回START_STICKY，确保服务即使被杀死后会重启
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 停止录制时释放资源
        stopRecording();
//        if (cameraExecutor != null) {
//            cameraExecutor.shutdown();
//        }
        new Handler().postDelayed(() -> {
            if (cameraProvider != null) {
                cameraProvider.unbindAll();
                Log.d(TAG, "onDestroy: cameraProvider.unbindAll()");
            }

        }, 500);  // 每500毫秒检查一次

    }

    // 创建通知
    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "video_recording_channel")
                .setContentTitle("录像")
                .setContentText("正在录音录像")
                .setSmallIcon(com.google.android.material.R.drawable.mtrl_ic_arrow_drop_down)
                .setOngoing(true); // 设置为持续显示

        return builder.build();
    }

    // 启动视频和音频录制
    private void startRecording() {
        Log.d(TAG, "Starting video and audio recording...");

        // 启动视频录制
        startVideoRecording(getApplicationContext());

    }

    // 停止录制
    private void stopRecording() {
        Log.d(TAG, "stopRecording() Stopping video and audio recording...");

        // 停止视频录制
        stopVideoRecording();

    }

    // 视频录制逻辑
    // Video recording start method
    public void startVideoRecording(Context context) {
        Log.d(TAG, "startVideoRecording() is called");
        if (videoCapture == null){
            return;
        }
        Log.d(TAG, "videoCapture is not null");
        // 获取当前时间作为文件名
        String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());


        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            //contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video  Environment.DIRECTORY_MOVIES");
            contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES+ "/AudioAPP");
        }

        MediaStoreOutputOptions mediaStoreOutputOptions = new MediaStoreOutputOptions.Builder(
                context.getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build();
        //Log.d(TAG, outputFile.getParent());
        PendingRecording pendingRecording = videoCapture.getOutput()
                .prepareRecording(context, mediaStoreOutputOptions);

        // 加声音，但是加了声音，也会被压缩，音频也不能用，没必要
//        if (PermissionChecker.checkSelfPermission(
//                context,
//                Manifest.permission.RECORD_AUDIO
//        ) == PermissionChecker.PERMISSION_GRANTED) {
//            pendingRecording = pendingRecording.withAudioEnabled();
//        }
        recording = pendingRecording.start(
                ContextCompat.getMainExecutor(context),
                recordEvent -> {
                    if (recordEvent instanceof VideoRecordEvent.Start) {
                        // 通知 Fragment 录制开始
                        Log.d(TAG, "recordEvent instanceof VideoRecordEvent.Start 录制开始");
                        sendBroadcast(new Intent("com.example.START_RECORDING"));
                    } else if (recordEvent instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) recordEvent;
                        Log.d(TAG, "VideoRecordEvent.Finalize 录制结束");
                        if (!finalizeEvent.hasError()) {
                            Uri videoUri = finalizeEvent.getOutputResults().getOutputUri();
                            //String inputFilePath = getFilePathFromUri(videoUri);
                            String outputFilePath = getFilePathFromUri(videoUri);

                            // 压缩视频
                            //compressVideo(inputFilePath, outputFilePath);

                            String msg = "Video capture succeeded: " + outputFilePath;
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                        }
                        else {
                            Log.e(TAG, "Video capture ends with error: " + finalizeEvent.getError());
                        }
                        // 通知 Fragment 录制停止
                        sendBroadcast(new Intent("com.example.STOP_RECORDING"));
                    }
                }
        );
    }

    private void stopVideoRecording() {
        // 停止视频录制的逻辑
        Log.d(TAG, "stopVideoRecording() Video recording stopped");
        if (recording != null) {
            Log.d(TAG, "recording != null");
            recording.stop();
            Log.d(TAG, "recording.stop(); ");
            new Handler().postDelayed(() -> {
                recording = null;
                Log.d(TAG, "recording = null; ");

            }, 500);  // 每500毫秒检查一次

        }
        // 发送广播通知 Activity 或 Fragment 录制已停止
//        Intent intent = new Intent("com.example.ACTION_VIDEO_RECORDING_STOPPED");
//        sendBroadcast(intent);
    }


    public void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        Log.d(TAG, "startCamera() is called");
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                // Preview setup
                Preview preview = new Preview.Builder().build();
                // Assuming viewFinder is a TextureView or SurfaceView
                //preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                // ImageCapture setup (for taking photos)
                ImageCapture imageCapture = new ImageCapture.Builder().build();

                // VideoCapture setup
                Recorder recorder = new Recorder.Builder().setExecutor(cameraExecutor)
                        .setQualitySelector(QualitySelector.from(Quality.LOWEST, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);
                Log.d(TAG, "videoCapture2 = VideoCapture.withOutput(recorder)");
                if (videoCapture != null){
                    Log.d(TAG, "videoCapture != null");
                }


                // Select back camera
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                // Unbind use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                        new LifecycleOwner() {
                            @NonNull
                            @Override
                            public Lifecycle getLifecycle() {
                                return new Lifecycle() {
                                    @Override
                                    public void addObserver(@NonNull LifecycleObserver observer) {
                                        // 不做任何操作
                                    }

                                    @Override
                                    public void removeObserver(@NonNull LifecycleObserver observer) {
                                        // 不做任何操作
                                    }

                                    @NonNull
                                    @Override
                                    public State getCurrentState() {
                                        return State.STARTED;  // 在服务启动时相当于"活动"状态
                                    }
                                };
                            }
                        }, // Typically use Fragment or Activity's lifecycle
                        cameraSelector,
                        //preview,
                        videoCapture
                );

                // 在这里启动录制
                startRecording();

            } catch (Exception exc) {
                Log.e("VideoRecordingService", "Use case binding failed", exc);
            }
        }, ContextCompat.getMainExecutor(this));
    }


//    private void compressVideo(String inputFilePath, String outputFilePath) {
//        String command = "-i " + inputFilePath + " -c:v mpeg4  -b:v 800k -vf scale=640:-2 -r 5 " + outputFilePath;
//        Log.d("FFmpegCommand", "Command: " + command);
//        FFmpegKit.executeAsync(command, session -> {
//            if (session.getReturnCode().isValueSuccess()) {
//                File originalFile = new File(inputFilePath);
////                if (originalFile.exists()) {
////                    boolean isDeleted = originalFile.delete();
////                    if (isDeleted) {
////                        Log.d("VideoCompression", "Original video deleted successfully.");
////                    } else {
////                        Log.e("VideoCompression", "Failed to delete the original video.");
////                    }
////                }
//             ;
//
//            } else {
//                // 切换到主线程显示失败消息
//                String errorLog = session.getOutput();
//                Log.e("FFmpegError", "Command failed: " + errorLog);
//                ;
//            }
//        });
//    }

    private String getFilePathFromUri(Uri uri) {
        String filePath = null;
        String[] projection = {MediaStore.Video.Media.DATA};

        try (Cursor cursor = getApplicationContext().getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                filePath = cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting file path from Uri: " + e.getMessage());
        }
        // 如果无法通过URI获取路径，直接使用传统方法
        if (filePath == null) {
            filePath = Environment.getExternalStorageDirectory().getAbsolutePath()
                    + "/Movies/"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date())
                    + ".mp4";
        }
        return filePath;
    }

}

