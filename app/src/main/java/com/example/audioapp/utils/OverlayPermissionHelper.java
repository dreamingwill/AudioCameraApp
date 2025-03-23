package com.example.audioapp.utils;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

public class OverlayPermissionHelper {
    public static final int REQUEST_CODE_OVERLAY_PERMISSION = 1002;

    public static void verifyOverlayPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(activity)) {
                // 没有权限，跳转到设置页面，让用户授权
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION);
                Toast.makeText(activity, "请授权悬浮窗权限以显示通知", Toast.LENGTH_SHORT).show();
            }
        }
    }
}