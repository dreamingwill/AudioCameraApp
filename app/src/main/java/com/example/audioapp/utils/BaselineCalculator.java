package com.example.audioapp.utils;

import com.example.audioapp.entity.CapturedData;

import java.util.List;

public class BaselineCalculator {
    // 计算用户的平均 Valence、Arousal 和标准差
    public static BaselineStats computeBaseline(List<CapturedData> history) {
        if (history == null || history.isEmpty()) {
            return new BaselineStats(0, 0, 0, 0);
        }
        float sumValence = 0, sumArousal = 0;
        for (CapturedData data : history) {
            sumArousal += data.avValues[0];
            sumValence += data.avValues[1];
        }
        int n = history.size();
        float avgArousal = sumArousal / n;
        float avgValence = sumValence / n;

        // 计算标准差
        float sumSqArousal = 0, sumSqValence = 0;
        for (CapturedData data : history) {
            float diffA = data.avValues[0] - avgArousal;
            float diffV = data.avValues[1] - avgValence;
            sumSqArousal += diffA * diffA;
            sumSqValence += diffV * diffV;
        }
        float stdArousal = (float) Math.sqrt(sumSqArousal / n);
        float stdValence = (float) Math.sqrt(sumSqValence / n);

        return new BaselineStats(avgArousal, avgValence, stdArousal, stdValence);
    }

    // 用于存放计算结果
    public static class BaselineStats {
        public float avgArousal;
        public float avgValence;
        public float stdArousal;
        public float stdValence;

        public BaselineStats(float avgArousal, float avgValence, float stdArousal, float stdValence) {
            this.avgArousal = avgArousal;
            this.avgValence = avgValence;
            this.stdArousal = stdArousal;
            this.stdValence = stdValence;
        }
    }
}
