package com.example.audioapp.utils;
import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class ModelLoader {
    static String TAG = "ModelLoader";
    // 定义归一化参数（与训练时一致）
    private static final float[] NORM_MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] NORM_STD = {0.229f, 0.224f, 0.225f};
    public static final String modelPath = "lc_best.pt";
    public static final String exampleImgPath = "mask.jpg";
    private static Module module;
    private Bitmap exampleBitmap = null;

    public ModelLoader(AssetManager assetManager) {
        try {
            // 从 assets 复制模型到临时文件（Android 无法直接加载 assets 中的文件）
            InputStream inputStream = assetManager.open(modelPath);
            File modelFile = File.createTempFile("model", ".pt");
            copyInputStreamToFile(inputStream, modelFile);

            // 加载模型并保存为成员变量
            this.module = Module.load(modelFile.getAbsolutePath());

            //
            this.exampleBitmap = BitmapFactory.decodeStream(assetManager.open(exampleImgPath));
            Log.d(TAG, "ModelLoader: 模型加载成功:"+modelPath);

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "加载模型失败: " + e.getMessage());
        }
    }
    

    // 从 assets 加载模型

    // 运行模型
    public float[] runModel(Bitmap inputBitmap) {

        if (module == null) {
            throw new IllegalStateException("模型未成功加载");
        }
        // 调整图像尺寸为模型输入要求（假设模型输入为 224x224）
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(inputBitmap, 224, 224, true);
        // 2. 将 Bitmap 转换为归一化的浮点数组（PyTorch需要的格式）
        Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                resizedBitmap,
                NORM_MEAN,
                NORM_STD
        );
        Log.d("InputHash", Arrays.hashCode(inputTensor.getDataAsFloatArray()) + "");

        // 4. 执行推理
        Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();

        // 5. 获取输出结果
        return outputTensor.getDataAsFloatArray();
    }


    public Bitmap getExampleBitmap() {
        return exampleBitmap;
    }
    // 执行推理
//    public float[] predict(Module module, float[] inputData, int[] inputShape) {
//        if (module == null) {
//            throw new IllegalStateException("Model not loaded!");
//        }
//
//        // 将输入数据转换为 Tensor（假设输入是图像，形状为 [1, 3, 224, 224]）
//        Tensor inputTensor = Tensor.fromBlob(inputData, inputShape);
//
//        // 推理
//        Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();
//
//        // 获取输出结果
//        float[] output = outputTensor.getDataAsFloatArray();
//        return output;
//    }

    // 辅助方法：将 InputStream 复制到文件
    private static void copyInputStreamToFile(InputStream in, File file) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } finally {
            in.close();
        }
    }
}
