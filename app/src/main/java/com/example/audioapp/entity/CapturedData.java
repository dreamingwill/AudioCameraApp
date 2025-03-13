package com.example.audioapp.entity;

import android.graphics.Bitmap;

public class CapturedData {
    public Bitmap cameraBitmap;
    public Bitmap screenBitmap;  // 屏幕截图
    public float[] avValues; // 输出数组[0]=arousal, [1]=valence
    public long timestamp;

    public CapturedData(Bitmap cameraBitmap, Bitmap screenBitmap, float[] avValues, long timestamp) {
        this.cameraBitmap = cameraBitmap;
        this.screenBitmap = screenBitmap;
        this.avValues = avValues;
        this.timestamp = timestamp;
    }
}
