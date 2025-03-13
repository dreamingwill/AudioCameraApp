package com.example.audioapp.utils;

import androidx.camera.core.ImageProxy;

import java.nio.ByteBuffer;

public class YuvUtil {
    public static byte[] imageToNV21(ImageProxy image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width * height;
        int uvSize = width * height / 4;
        byte[] nv21 = new byte[ySize + uvSize * 2];

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

        yBuffer.get(nv21, 0, ySize);

        // NV21 格式要求交错存放 V 和 U
        int index = ySize;
        byte[] uBytes = new byte[uBuffer.remaining()];
        uBuffer.get(uBytes);
        byte[] vBytes = new byte[vBuffer.remaining()];
        vBuffer.get(vBytes);
        for (int i = 0; i < uBytes.length; i++) {
            nv21[index++] = vBytes[i];
            nv21[index++] = uBytes[i];
        }
        return nv21;
    }
}
