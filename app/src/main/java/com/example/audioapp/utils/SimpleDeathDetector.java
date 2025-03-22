package com.example.audioapp.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

public class SimpleDeathDetector {

    /**
     * 判断截图是否处于“死亡”状态
     * @param screenBitmap 游戏截图
     * @param brightnessThreshold 正常判定为死亡的亮度阈值（例如80，表示平均亮度低于80则认为死亡）
     * @param tooDarkThreshold 如果平均亮度低于此值（例如20），则认为可能是数据采集异常而非真实死亡
     * @return 如果画面平均亮度低于 brightnessThreshold 且不低于 tooDarkThreshold，则返回 true；否则返回 false
     *         如果平均亮度低于 tooDarkThreshold，则返回 false，并可在日志中标记数据异常
     */
    public static boolean isPlayerDead(Bitmap screenBitmap, int brightnessThreshold, int tooDarkThreshold) {
        if (screenBitmap == null) return false;

        // 如果是 HARDWARE 配置，则转换成 ARGB_8888 格式（只读即可，不需要可变）
        if (screenBitmap.getConfig() == Bitmap.Config.HARDWARE) {
            screenBitmap = screenBitmap.copy(Bitmap.Config.ARGB_8888, false);
        }

        int width = screenBitmap.getWidth();
        int height = screenBitmap.getHeight();
        int totalPixels = width * height;
        int[] pixels = new int[totalPixels];

        screenBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        long sumBrightness = 0;
        int validPixelCount = 0;
        int blackThreshold = 10;  // 判断黑色的阈值

        for (int color : pixels) {
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            int brightness = (r + g + b) / 3;
            if (brightness <= blackThreshold) {
                continue;
            }
            sumBrightness += brightness;
            validPixelCount++;
        }

        if (validPixelCount == 0) {
            Log.d("SimpleDeathDetector", "无有效像素，不作为死亡判定");
            return false;
        }

        int avgBrightness = (int) (sumBrightness / validPixelCount);
        Log.d("SimpleDeathDetector", "avgBrightness (排除黑色像素): " + avgBrightness);

        if (avgBrightness < tooDarkThreshold) {
            Log.d("SimpleDeathDetector", "画面过于黑暗，可能是采集异常，不作为死亡判定");
            return false;
        }

        return avgBrightness < brightnessThreshold;
    }



}
