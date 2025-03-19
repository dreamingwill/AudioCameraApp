package com.example.audioapp.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.face.FaceContour;
import com.google.mlkit.vision.face.FaceLandmark;
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
    private static final String TAG = "FaceDetectorHelper";
    // 期望的关键点数量
    private static final int EXPECTED_KEYPOINTS = 6;
    // 检测比例阈值，例如80%
    private static final double DETECTION_RATIO_THRESHOLD = 0.8;

    private static final int REQUIRED_VALID_POINTS = 8;
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
                        Face face = faces.get(0);
                        // 获取第一张人脸的边界框
                        Rect bounds = face.getBoundingBox();
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
                        Log.d(TAG, "cropFaceFromBitmap: faces.isEmpty()");
                    }
                })
                .addOnFailureListener(e -> {
                    e.printStackTrace();
                    callback.onFaceDetected(null);
                });
    }



    // 验证有效关键点数量
    private static boolean hasEnoughValidPoints(List<FaceLandmark> landmarks) {
        int validCount = 0;
        for (FaceLandmark landmark : landmarks) {
            // 验证关键点有效性的三个条件
            if (landmark != null) {
                validCount++;
            }
        }
        Log.d(TAG, "Valid landmarks: " + validCount);
        return validCount >= REQUIRED_VALID_POINTS;
    }

    // 检查核心关键点是否缺失
    private static boolean missingCrucialPoints(Face face, int[] crucialTypes) {
        for (int type : crucialTypes) {
            FaceLandmark landmark = face.getLandmark(type);
            assert landmark != null;
            Log.d(TAG, "missingCrucialPoints: "+ landmark);
            if (landmark == null) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFaceOccluded_2(Face face) {
        int detectedKeyPoints = 0;
        // 取出关键点
        if (face.getLandmark(FaceLandmark.LEFT_EYE) != null) detectedKeyPoints++;
        if (face.getLandmark(FaceLandmark.RIGHT_EYE) != null) detectedKeyPoints++;
        if (face.getLandmark(FaceLandmark.NOSE_BASE) != null) detectedKeyPoints++;
        if (face.getLandmark(FaceLandmark.MOUTH_BOTTOM) != null) detectedKeyPoints++;
        if (face.getLandmark(FaceLandmark.MOUTH_LEFT) != null) detectedKeyPoints++;
        if (face.getLandmark(FaceLandmark.MOUTH_RIGHT) != null) detectedKeyPoints++;

        double ratio = (double) detectedKeyPoints / EXPECTED_KEYPOINTS;

        // 仅根据数量判断
        return ratio < DETECTION_RATIO_THRESHOLD;  // 被遮挡
    }

    // 回调接口，用于返回检测结果
    public interface FaceDetectionCallback {
        void onFaceDetected(@Nullable Bitmap faceBitmap);
    }
}
