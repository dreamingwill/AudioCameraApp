package com.example.audioapp.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.List;
import java.util.concurrent.Executors;

public class PrivacyUtil {
    private Context context;

    public PrivacyUtil(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 对 Bitmap 进行隐私保护：识别文字区域，并对这些区域进行模糊处理。
     * 如果区域非常小，则跳过模糊。
     *
     * @param bitmap 原始截图
     * @return 处理后（文字区域模糊）的 Bitmap
     */
    public Bitmap blurTextRegions(Bitmap bitmap) {
        if (bitmap == null) return null;
        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        InputImage image = InputImage.fromBitmap(mutableBitmap, 0);

        // 注意：这段代码必须在后台线程中执行！
        //FaceRecognizerResult result = null;
        TextRecognizer recognizer = TextRecognition.getClient(new TextRecognizerOptions.Builder().setExecutor(Executors.newSingleThreadExecutor()).build());
        try {
            // 在后台线程中调用 Tasks.await
            Text textResult = Tasks.await(recognizer.process(image));
            List<Text.TextBlock> blocks = textResult.getTextBlocks();
            if (blocks != null && !blocks.isEmpty()) {
                Canvas canvas = new Canvas(mutableBitmap);
                for (Text.TextBlock block : blocks) {
                    Rect bounds = block.getBoundingBox();
                    if (bounds != null) {
                        int left = Math.max(0, bounds.left);
                        int top = Math.max(0, bounds.top);
                        int right = Math.min(mutableBitmap.getWidth(), bounds.right);
                        int bottom = Math.min(mutableBitmap.getHeight(), bounds.bottom);
                        int width = right - left;
                        int height = bottom - top;
                        if (width < 20 || height < 20) continue;
                        Bitmap textRegion = Bitmap.createBitmap(mutableBitmap, left, top, width, height);
                        Bitmap blurredRegion = blurBitmap(textRegion, 25f);
                        canvas.drawBitmap(blurredRegion, left, top, null);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return mutableBitmap;
        }
        return mutableBitmap;
    }


    /**
     * 使用 RenderScript 对 Bitmap 进行模糊处理
     *
     * @param bitmap 待模糊的 Bitmap
     * @param radius 模糊半径，建议范围 (0, 25]
     * @return 模糊处理后的 Bitmap
     */
    private Bitmap blurBitmap(Bitmap bitmap, float radius) {
        try {
            Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
            RenderScript rs = RenderScript.create(context);
            Allocation input = Allocation.createFromBitmap(rs, bitmap);
            Allocation outputAlloc = Allocation.createFromBitmap(rs, output);
            ScriptIntrinsicBlur blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            blurScript.setRadius(radius); // radius 取值 0 < radius <= 25
            blurScript.setInput(input);
            blurScript.forEach(outputAlloc);
            outputAlloc.copyTo(output);
            rs.destroy();
            return output;
        } catch (Exception e) {
            Log.e("PrivacyUtil", "模糊处理失败: " + e.getMessage());
            return null;
        }
    }

    public interface OnBlurCompleteListener {
        void onComplete(Bitmap processedBitmap);
    }

    public void blurTextRegionsAsync(final Bitmap bitmap, final OnBlurCompleteListener listener) {
        new Thread(() -> {
            Bitmap processed = blurTextRegions(bitmap);
            // 回调到主线程
            new Handler(Looper.getMainLooper()).post(() -> listener.onComplete(processed));
        }).start();
    }

}
