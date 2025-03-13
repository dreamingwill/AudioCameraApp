package com.example.audioapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class MediaPlayerService extends Service {
    private MediaPlayer player;
    private final String TAG = "MediaPlayerService";
    @Override
    public void onCreate() {
        super.onCreate();

        // 创建通知渠道（仅适用于 API 26+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "audio_channel",
                    "Audio Playback",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("音频播放服务渠道");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        // 初始化 MediaPlayer
        player = new MediaPlayer();
        player.setLooping(true);

        // 构建点击通知后跳转的 PendingIntent（例如跳转到 MainActivity）
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        // 构建前台通知
        Notification notification = new NotificationCompat.Builder(this, "audio_channel")
                .setContentTitle("正在播放音频")
                .setContentText("点击停止")
                .setSmallIcon(R.drawable.ic_launcher_foreground)  // 请确保该图标符合通知要求
                .setContentIntent(pendingIntent)
                .build();

        // 启动前台服务
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: ");
        try {
            AssetFileDescriptor afd = getAssets().openFd("fmcw_signal_cos.wav");
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            player.prepare();
            player.start();
        } catch (IOException e) {
            e.printStackTrace();
        }



        return START_STICKY; // 确保 Service 被杀后重启
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.stop();
            player.release();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
