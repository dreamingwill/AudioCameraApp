package com.example.audioapp.utils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import com.example.audioapp.entity.CapturedData;
import com.example.audioapp.BuildConfig;
public class ChatGptHelper {
    private static final String TAG = "ChatGptHelper";
    // 请替换为你的实际 API 密钥和 API 端点
    private static final String API_KEY = BuildConfig.OPENAI_API_KEY;
    private static final String API_URL = "https://aigc.x-see.cn/v1/chat/completions";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    // 系统提示：专业、友善的游戏心理辅导助手
    private static final String SYSTEM_PROMPT = "你是一位专业的游戏心理辅导助手,擅长分析游戏玩家的心理状态和行为模式。你的主要职责是:\n" +
            "1. 通过游戏截图分析玩家的游戏表现和失败原因\n" +
            "2. 基于玩家的情绪数据和行为特征提供个性化的心理支持\n" +
            "3. 给出具有建设性的游戏建议,帮助玩家重建信心\n" +
            "4. 用温和幽默的方式缓解玩家的负面情绪\n\n" +
            "请以专业、友善且富有同理心的态度与玩家互动。";

    // 任务提示模板：只使用 CapturedData 中的信息
    // 其中 avValues[0] 为 arousal，avValues[1] 为 valence
    private static final String TASK_PROMPT_TEMPLATE = "请分析以下玩家的情绪数据：\n" +
            "激活程度(Ar): %.2f\n" +
            "情绪价值(Va): %.2f\n" +
            "时间戳: %d\n" +
            "回复不多于15个汉字\n请用简短温和幽默的语言，给出安抚建议。";

    private final OkHttpClient client;

    public ChatGptHelper() {
        client = new OkHttpClient();
    }

    /**
     * 使用 CapturedData 中的信息生成对 GPT 的请求，并返回安抚建议。
     * 其中将 CapturedData 的 screenBitmap 作为图片数据上传（通过转换为 data URL）。
     *
     * @param data     CapturedData 对象，包含情绪数据和屏幕截图
     * @param callback 回调接口，返回 GPT 回复或错误信息
     */
    public void getInterventionResponse(CapturedData data, ChatGptCallback callback) {
        // 根据 CapturedData 构建文本提示
        String promptText = String.format(TASK_PROMPT_TEMPLATE,
                data.avValues[0], data.avValues[1], data.timestamp);

        // 构建用户消息内容，使用 JSON 数组形式
        JSONArray contentArray = new JSONArray();
        try {
            // 第一项：文本信息
            JSONObject textObj = new JSONObject();
            textObj.put("type", "text");
            textObj.put("text", promptText);
            contentArray.put(textObj);

            // 第二项：图片数据，使用 CapturedData 的 screenBitmap
            if (data.screenBitmap != null) {
                JSONObject imgObj = new JSONObject();
                imgObj.put("type", "image_url");
                JSONObject urlObj = new JSONObject();
                // 将 Bitmap 转为 data URL
                String dataUrl = bitmapToDataUrl(data.screenBitmap);
                urlObj.put("url", dataUrl);
                imgObj.put("image_url", urlObj);
                contentArray.put(imgObj);
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
            jsonBody.put("model", "gpt-4o-mini");  // 根据实际情况调整模型名称
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
    private String bitmapToDataUrl(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // 可根据需求调整压缩质量
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        byte[] imageBytes = baos.toByteArray();
        String base64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
        Log.d(TAG, "bitmapToDataUrl: "+"data:image/jpeg;base64," + base64);
        return "data:image/jpeg;base64," + base64;
    }

    // 回调接口
    public interface ChatGptCallback {
        void onSuccess(String reply);
        void onFailure(String error);
    }


}


