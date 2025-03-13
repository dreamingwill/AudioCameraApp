package com.example.audioapp.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;

import androidx.camera.core.ImageProxy;

public class YuvToRgbConverter {
    private RenderScript rs;
    private ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;

    public YuvToRgbConverter(Context context) {
        rs = RenderScript.create(context);
        yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
    }

    public void yuvToRgb(ImageProxy image, Bitmap output) {
        // 将 ImageProxy 转换为 NV21 格式
        byte[] nv21 = YuvUtil.imageToNV21(image);
        Allocation in = Allocation.createSized(rs, Element.U8(rs), nv21.length);
        Allocation out = Allocation.createFromBitmap(rs, output);
        in.copyFrom(nv21);
        yuvToRgbIntrinsic.setInput(in);
        yuvToRgbIntrinsic.forEach(out);
        out.copyTo(output);
    }
}