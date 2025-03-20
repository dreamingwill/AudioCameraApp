package com.example.audioapp.utils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import com.example.audioapp.entity.CapturedData;
import com.example.audioapp.BuildConfig;
public class ChatGptHelper {
    private static final String TAG = "ChatGptHelper";
    // 请替换为你的实际 API 密钥和 API 端点
    private static final String API_KEY = BuildConfig.OPENAI_BASE_KEY_2;
    private static final String API_URL = BuildConfig.OPENAI_BASE_URL_2;
    private static final String MODEL_NAME = "grok-2-vision-1212"; //grok-2-vision-1212,grok-3
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    // 系统提示：专业、友善的游戏心理辅导助手

    // 系统提示：统一说明你的角色和要求
    private static final String SYSTEM_PROMPT = "你是一位专业的游戏心理辅导助手，擅长分析游戏玩家的心理状态和行为模式。你的主要职责是：\n" +
            "1. 通过游戏截图分析玩家的表现和当前局势；\n" +
            "2. 提供个性化心理支持和具体操作建议，帮助玩家重建信心；\n" +
            "3. 用温和幽默的语言缓解负面情绪；\n" +
            "4. 必须紧扣玩家正在玩的游戏情况。\n" +
            "回复不超过15个汉字，请以专业、友善且富有同理心的态度与玩家互动。";

    // 根据不同游戏类型生成任务提示文本
    // 例如：1-王者荣耀，2-金铲铲，3-枪战游戏，4-其他游戏
    private static final String TASK_PROMPT_KING = "【王者荣耀】请判断截图是否显示玩家死亡；若死亡，请分析失败原因并给出鼓励；若未死亡，请结合团战情况提出针对性建议。";
    private static final String TASK_PROMPT_GOLD = "【金铲铲】请分析截图中玩家局势，指出潜在问题，并给出具体可行的建议与鼓励。";
    private static final String TASK_PROMPT_SHOOTER = "【枪战游戏】请分析截图中玩家的战局，指出团队协作或战术方面的问题，提供针对性建议和鼓励。";
    private static final String TASK_PROMPT_OTHERS = "【其他游戏】请根据截图内容分析当前游戏情况，给出针对性建议，若图片内容与选定游戏不符，请以图片实际内容为准。";

    // 根据游戏类型生成 task prompt
    // gameType: 1-王者荣耀, 2-金铲铲, 3-枪战游戏, 4-其他游戏
    // 根据游戏类型返回对应的任务提示
    private static String getTaskPrompt(int gameType) {
        switch (gameType) {
            case 1:
                return TASK_PROMPT_KING;
            case 2:
                return TASK_PROMPT_GOLD;
            case 3:
                return TASK_PROMPT_SHOOTER;
            case 4:
            default:
                return TASK_PROMPT_OTHERS;
        }
    }

    private final OkHttpClient client;

    public ChatGptHelper() {
        client = new OkHttpClient();
    }

    /**
     * 发送请求给大模型进行安抚回复，输入为 captureBuffer 中所有数据和用户选择的游戏类型。
     *
     * @param captureBuffer 捕获的最近 5 秒内的传感器数据（和截图）
     * @param gameType 用户选择的游戏类型（1：王者荣耀，2：金铲铲，3：枪战游戏，4：其他游戏）
     * @param callback 回调接口
     */
    @SuppressLint("DefaultLocale")
    public void getInterventionResponse(List<CapturedData> captureBuffer, int gameType, ChatGptCallback callback) {
        if (captureBuffer == null || captureBuffer.isEmpty()) {
            callback.onFailure("captureBuffer为空");
            return;
        }
        // 构建 prompt 文本
        StringBuilder promptBuilder = new StringBuilder();
        // 首先加入针对游戏类型的任务提示
        promptBuilder.append(getTaskPrompt(gameType));
        promptBuilder.append("\n");
        // 添加近5秒内每个时刻的情绪数据
        promptBuilder.append("Arousal表示情感的强度或活跃程度，Valence情感的积极或消极性。近5秒情绪数据 (Arousal,Valence): ");
        for (CapturedData data : captureBuffer) {
            promptBuilder.append(String.format("(%.2f,%.2f),", data.avValues[0], data.avValues[1]));
        }
        promptBuilder.append("\n如果上传的图片与选定游戏不一致，请以图片内容为准，但不能说出图片与要求不一致，也不能说‘图片’两个字；应该继续根据图片判断用户在做什么，说些安慰、鼓励的话。");
        promptBuilder.append("\n回复要求：请用温和幽默的语言，给出安抚建议，不超过15个汉字。");

        String promptText = promptBuilder.toString();
        Log.d("ChatGptHelper", "Constructed Prompt: " + promptText);
        // 构建用户消息内容，使用 JSON 数组形式
        JSONArray contentArray = new JSONArray();
        try {
            // 第一项：文本信息
            JSONObject textObj = new JSONObject();
            textObj.put("type", "text");
            textObj.put("text", promptText);
            contentArray.put(textObj);

            // 第二项：图片数据，使用 CapturedData 的 screenBitmap
            // 裁剪 2000*768以内
            int[] indicesToSend = {0, 2, 3, 4,};
            for (int idx : indicesToSend) {
                if (captureBuffer.size() > idx) {
                    CapturedData data = captureBuffer.get(idx);
                    if (data.screenBitmap != null) {
                        JSONObject imgObj = new JSONObject();
                        imgObj.put("type", "image_url");
                        JSONObject urlObj = new JSONObject();
                        // 这里设置质量为 30，达到降低图片大小的效果
                        String dataUrl = bitmapToDataUrl(data.screenBitmap, 20);
                        urlObj.put("url", dataUrl);
                        imgObj.put("image_url", urlObj);
                        contentArray.put(imgObj);
                    }
                }
            }


        } catch (JSONException e) {
            callback.onFailure("JSON构建错误: " + e.getMessage());
            return;
        }

        // 构建消息数组，系统消息 + 用户消息
        JSONArray messagesArray = new JSONArray();
        try {
            // 系统消息
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", SYSTEM_PROMPT);
            messagesArray.put(systemMsg);

            // 用户消息
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", contentArray);
            messagesArray.put(userMsg);
        } catch (JSONException e) {
            callback.onFailure("消息构建错误: " + e.getMessage());
            return;
        }

        // 构建请求体
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", MODEL_NAME);  // 根据实际情况调整模型名称
            jsonBody.put("messages", messagesArray);
            jsonBody.put("temperature", 0.7);
        } catch (JSONException e) {
            callback.onFailure("请求构建错误: " + e.getMessage());
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer " + API_KEY)
                .post(body)
                .build();

        // 异步请求
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e.getMessage());
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onFailure("Unexpected code " + response);
                    return;
                }
                String responseBody = response.body().string();
                try {
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    JSONArray choices = jsonResponse.getJSONArray("choices");
                    if (choices.length() > 0) {
                        JSONObject messageObj = choices.getJSONObject(0).getJSONObject("message");
                        String reply = messageObj.getString("content");
                        callback.onSuccess(reply);
                    } else {
                        callback.onFailure("没有返回消息");
                    }
                } catch (JSONException e) {
                    callback.onFailure("JSON解析错误: " + e.getMessage());
                }
            }
        });
    }

    /**
     * 将 Bitmap 转为 data URL 字符串（JPEG 格式）。
     *
     * @param bitmap 要转换的 Bitmap
     * @return data URL 字符串
     */
    // 压缩，resize一下。改成png试试。
    private String bitmapToDataUrl(Bitmap bitmap, int quality) {
        // 最大允许的尺寸
        int maxWidth = 1000; // 2000
        int maxHeight = 384; //768
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // 计算缩放因子，确保宽度不超过 maxWidth，且高度不超过 maxHeight
        float scale = 1.0f;
        if (width > maxWidth || height > maxHeight) {
            scale = Math.min((float) maxWidth / width, (float) maxHeight / height);
        }

        // 如果需要缩放，则生成新的 Bitmap
        Bitmap resizedBitmap = bitmap;
        if (scale < 1.0f) {
            int newWidth = Math.round(width * scale);
            int newHeight = Math.round(height * scale);
            resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            Log.d(TAG, "Bitmap resized to: " + newWidth + "x" + newHeight);
        } else {
            Log.d(TAG, "Bitmap size is within limit, no resizing.");
        }

        // 使用 PNG 格式压缩 Bitmap
        // 注意：PNG 格式忽略 quality 参数
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        byte[] imageBytes = baos.toByteArray();

        // 转换为 Base64 字符串
        String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        String dataUrl = "data:image/jpeg;base64," + base64;
        Log.d(TAG, "bitmapToDataUrl: " + dataUrl);
        return dataUrl;
    }

    // 回调接口
    public interface ChatGptCallback {
        void onSuccess(String reply);
        void onFailure(String error);
    }


}


