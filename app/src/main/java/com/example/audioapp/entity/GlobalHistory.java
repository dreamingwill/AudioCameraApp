package com.example.audioapp.entity;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GlobalHistory {
    private static final String TAG = "GlobalHistory";
    // 可以放在单例、Application 或者数据库中，这里简单用静态 List 演示
    private static final List<CapturedData> globalVAList = new ArrayList<>();

    public static final int HISTORY_PART_SAMPLES_NUM =210;
    public static final int HISTORY_MAX_SAMPLES_NUM =6000;
    // 标志位置，表示从 globalVAList 的哪个索引开始保存到 CSV 文件
    private static int saveStartIndex = 0;

    // 更新全局历史
    public static synchronized void updateGlobalHistory(CapturedData newData) {
        if (globalVAList.size() >= HISTORY_MAX_SAMPLES_NUM){
            globalVAList.remove(0);
        }
        globalVAList.add(newData);
        // 如果担心过大，可做清理或只保存最近N天的数据

    }

    public static synchronized List<CapturedData> getPartGlobalVAList() {
        int size = globalVAList.size();
        int fromIndex = Math.max(size - HISTORY_PART_SAMPLES_NUM, 0); // 计算起始索引，确保不越界
        return new ArrayList<>(globalVAList.subList(fromIndex, size)); // 返回副本
    }
    public  static int getSize(){
        return globalVAList.size();
    }
    /**
     * 从指定的 CSV 文件中加载历史记录，并更新 globalVAList。
     * CSV 文件格式：
     *  Timestamp,Arousal,Valence
     *  1623456789012,0.65,-0.15
     *  ...
     *
     * @param csvFile CSV 文件路径
     */
    public static synchronized void loadFromCSV(File csvFile) {
        // 清空当前历史记录
        globalVAList.clear();

        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String line;
            // 读取表头
            line = reader.readLine();
            if (line == null) {
                return;
            }
            // 按行读取数据
            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length < 3) {
                    continue; // 数据不完整，跳过
                }
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault());
                    Date date = sdf.parse(tokens[0].trim());
                    assert date != null;
                    long timestamp = date.getTime();
                    float arousal = Float.parseFloat(tokens[1].trim());
                    float valence = Float.parseFloat(tokens[2].trim());
                    float[] avValues = new float[]{arousal, valence};

                    // 注意：这里没有图像数据，所以 cameraBitmap 和 screenBitmap 设置为 null
                    CapturedData data = new CapturedData(avValues, timestamp);
                    globalVAList.add(data);
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                    // 如果解析失败，可以记录日志，并跳过这一行
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }
            Log.d(TAG, "loadFromCSV: "+globalVAList.size());
        } catch (IOException e) {

            e.printStackTrace();
        }
    }

    public static synchronized void saveToCSV(File csvFile) {
        try (FileOutputStream fos = new FileOutputStream(csvFile)) {
            String header = "Timestamp,Arousal,Valence\n";
            fos.write(header.getBytes());
            int start = Math.max(0, saveStartIndex);
            for (int i = start; i < globalVAList.size(); i++) {
                CapturedData data = globalVAList.get(i);
                String timestampStr = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault())
                        .format(new Date(data.timestamp));
                String line = timestampStr + "," + data.avValues[0] + "," + data.avValues[1] + "\n";
                fos.write(line.getBytes());
            }
            fos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    // 设置保存时的起始标志位置（例如：记录当前数据个数，后续只保存新增数据）
    public static synchronized void setSaveStartIndex(int index) {
        saveStartIndex = index;
    }

    // 获取当前保存标志位置
    public static synchronized int getSaveStartIndex() {
        return saveStartIndex;
    }
}
