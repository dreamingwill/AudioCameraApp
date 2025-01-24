package com.example.audioapp;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.media.MediaRecorder;
import android.hardware.display.VirtualDisplay;
import android.content.Context;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.audioapp.utils.FileUtil;

import java.io.IOException;
import android.hardware.display.DisplayManager;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

public class ScreenRecordingService extends Service {
    private Handler handler = new Handler(Looper.getMainLooper()); // 主线程的 Handler
    private static final String CHANNEL_ID = "RecordingServiceChannel";
    private static final String TAG = "ScreenRecordingService";
    private boolean isRecording = false;  // 由Service来控制
    private MediaProjection mediaProjection;
    private MediaRecorder mediaRecorder;
    private VirtualDisplay virtualDisplay;
    private int screenDensity;
    private String recordedFilePath = "";
    public static final String ACTION_UPDATE_RECORDING_STATE = "com.example.AudioCameraApp48k.UPDATE_RECORDING_STATE";
    public static final String EXTRA_RECORDING_STATE = "recording_state";  // 0 for stopped, 1 for recording

    private void sendRecordingStateUpdate(int state) {
        Intent intent = new Intent(ACTION_UPDATE_RECORDING_STATE);
        intent.putExtra(EXTRA_RECORDING_STATE, state);
        sendBroadcast(intent);  // Send broadcast
    }

    @Override
    public void onCreate() {
        super.onCreate();



        startForeground(1, createNotification());
        Log.d(TAG, "onCreate: startForeground(1, notification);");

    }
    private Notification createNotification() {
        // 创建通知渠道（Android 8.0 以上必须使用通知渠道）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "screen_recording_channel", "Screen Recording", NotificationManager.IMPORTANCE_LOW);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }

        // 创建并返回通知
        return new NotificationCompat.Builder(this, "screen_recording_channel")
                .setContentTitle("Screen Recording")
                .setContentText("Recording in progress...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)  // 设置图标
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onDestroy() {
        super.onDestroy();
        stopRecording();
    }
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: ");
// 设置前台服务，并指定类型为 MEDIA_PROJECTION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(1, createNotification());  // 低于 Android 10 的设备
        }
        // 开始录屏的代码逻辑
        if (intent != null) {
            int resultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra("data");

            // 调用之前写的 startRecording 方法
            if (resultCode == Activity.RESULT_OK && data != null) {
                Log.d(TAG, "onStartCommand: startRecording()");
                startRecording(resultCode, data);
            }else {
                Log.d(TAG, "onStartCommand: resultCode or data is null");
            }
        }
        
        // 返回START_STICKY，确保服务即使被杀死后会重启
        return START_STICKY;
    }

    public void startRecording(int resultCode, Intent data) {
        if (!isRecording) {
            try {
                // 执行录制操作
                isRecording = true;  // 更新录制状态

                // 发送广播更新 UI
                sendBroadcast(new Intent(ACTION_UPDATE_RECORDING_STATE).putExtra("isRecording", isRecording));
                Log.d(TAG, "startRecording: sendBroadcast isRecording");

                // 设置并启动录制
                MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                setupMediaRecorder();
                startVirtualDisplay();
                mediaRecorder.start();

                // 发送广播，通知 UI 更新
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ScreenRecordingService.this, "Screen Recording Started", Toast.LENGTH_SHORT).show();
                    }
                });
                Log.d(TAG, "开始录制");

            } catch (Exception e) {
                // 录制失败时的处理
                isRecording = false;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ScreenRecordingService.this, "Failed to start screen recording", Toast.LENGTH_SHORT).show();
                    }
                });
                Log.e(TAG, "Recording failed: ", e);
            }
        } else {
            Log.d(TAG, "startRecording: now in ScreenRecordingService isRecording =" + isRecording);
        }


    }

    private void setupMediaRecorder() {
        try {
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recordedFilePath = FileUtil.getScreenVideoFileAbsolutePath(FileUtil.createFilename()+".mp4");
            mediaRecorder.setOutputFile(recordedFilePath);
            mediaRecorder.setVideoSize(720,480);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setVideoFrameRate(15);
            mediaRecorder.setVideoEncodingBitRate(1 * 1024 * 1024);
            mediaRecorder.prepare();
            Log.d(TAG, "setupMediaRecorder: mediaRecorder prepared");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void startVirtualDisplay() {
        screenDensity = getResources().getDisplayMetrics().densityDpi;
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenRecording",
                1280,
                720,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.getSurface(),
                null,
                null
        );
    }

    public void stopRecording() {
        // 停止录制的逻辑
        if (isRecording) {
            try {
                // 执行停止录制操作
                isRecording = false;  // 更新录制状态

                // 发送广播更新 UI
                sendBroadcast(new Intent(ACTION_UPDATE_RECORDING_STATE).putExtra("isRecording", isRecording));

                // 停止录制的代码，比如释放 MediaProjection 实例等
                // 例如： stopMediaProjection();
                if (mediaRecorder != null) {
                    mediaRecorder.stop();
                    mediaRecorder.reset();
                }
                if (virtualDisplay != null) {
                    virtualDisplay.release();
                    Log.d(TAG, "stopRecording: virtualDisplay released");
                }
                if (mediaProjection != null) {
                    mediaProjection.stop();
                }

                // 在主线程中显示 Toast
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        String msg = "Screen Recording Stopped. File saved to: " + recordedFilePath;
                        Toast.makeText(ScreenRecordingService.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
                Log.d(TAG, "停止录制");

            } catch (Exception e) {
                // 如果发生错误，显示错误信息
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ScreenRecordingService.this, "Failed to stop recording", Toast.LENGTH_SHORT).show();
                    }
                });
                Log.e(TAG, "Stop recording failed: ", e);
            }
        } else {
            Log.d(TAG, "stopRecording: it is not recording now.");
        }


    }
}
