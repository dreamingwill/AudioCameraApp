package com.example.audioapp.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import androidx.annotation.Nullable;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.List;

public class FaceDetectorHelper {

    /**
     * 异步检测 Bitmap 中的人脸，并通过回调返回裁剪后的人脸 Bitmap。
     * 如果没有检测到人脸，则回调返回 null。
     *
     * @param context  当前上下文
     * @param bitmap   要检测的 Bitmap
     * @param callback 回调接口，返回裁剪后的人脸 Bitmap 或 null
     */
    public static void cropFaceFromBitmap(Context context, Bitmap bitmap, FaceDetectionCallback callback) {
        // 配置人脸检测选项，这里采用快速模式
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build();

        FaceDetector detector = FaceDetection.getClient(options);
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        detector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces != null && !faces.isEmpty()) {
                        // 获取第一张人脸的边界框
                        Rect bounds = faces.get(0).getBoundingBox();
                        // 修正边界，确保不会超出 Bitmap 范围
                        int left = Math.max(0, bounds.left);
                        int top = Math.max(0, bounds.top);
                        int right = Math.min(bitmap.getWidth(), bounds.right);
                        int bottom = Math.min(bitmap.getHeight(), bounds.bottom);
                        // 裁剪人脸区域
                        Bitmap faceBitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
                        callback.onFaceDetected(faceBitmap);
                    } else {
                        callback.onFaceDetected(null);
                    }
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    callback.onFaceDetected(null);
                });
    }

    // 回调接口，用于返回检测结果
    public interface FaceDetectionCallback {
        void onFaceDetected(@Nullable Bitmap faceBitmap);
    }
}
