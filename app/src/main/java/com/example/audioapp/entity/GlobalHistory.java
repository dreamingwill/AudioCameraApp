package com.example.audioapp.entity;

import java.util.ArrayList;
import java.util.List;

public class GlobalHistory {
    // 可以放在单例、Application 或者数据库中，这里简单用静态 List 演示
    private static final List<CapturedData> globalVAList = new ArrayList<>();

    // 更新全局历史
    public static synchronized void updateGlobalHistory(CapturedData newData) {
        globalVAList.add(newData);
        // 如果担心过大，可做清理或只保存最近N天的数据
    }

    public static synchronized List<CapturedData> getGlobalVAList() {
        return new ArrayList<>(globalVAList); // 返回副本，避免外部改动原数据
    }
}
