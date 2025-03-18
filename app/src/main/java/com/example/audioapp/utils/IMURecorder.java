package com.example.audioapp.utils;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import org.supercsv.io.CsvMapWriter;
import org.supercsv.io.ICsvMapWriter;
import org.supercsv.prefs.CsvPreference;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

public class IMURecorder {
    private static final String TAG = "IMURecorder";
    // CSV 表头和时间格式
    private static final String[] HEADER = {"Timestamp", "AccX", "AccY", "AccZ", "GyroX", "GyroY", "GyroZ"};
    private static final String FILENAME_FORMAT = "yyyyMMdd_HHmmss_SSS";

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Context context;
    private boolean isRecording = false;

    // CSV 写入器
    private ICsvMapWriter csvWriter;
    private String filePath;

    // 用于存储最新的传感器数据
    private IMUData currentData;

    public IMURecorder(Context context) {
        this.context = context.getApplicationContext();
        currentData = new IMUData();
        sensorManager = (SensorManager) this.context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
    }

    /**
     * 开始记录 IMU 数据，并将数据写入指定 CSV 文件中
     * @param filePath CSV 文件保存路径（例如：context.getFilesDir().getAbsolutePath() + "/imu_data.csv"）
     */
    public void startRecording(String filePath) {
        if (isRecording) return;
        this.filePath = filePath;
        initCSVFile();
        // 注册监听器：可以使用 SENSOR_DELAY_GAME 或 50_000（微秒），根据需要调整
        if (accelerometer != null) {
            sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        if (gyroscope != null) {
            sensorManager.registerListener(sensorListener, gyroscope, SensorManager.SENSOR_DELAY_GAME);
        }
        isRecording = true;
        Log.d(TAG, "IMU recording started.");
    }

    /**
     * 停止记录 IMU 数据，并关闭 CSV 写入器
     */
    public void stopRecording() {
        if (!isRecording) return;
        sensorManager.unregisterListener(sensorListener);
        closeCSVWriter();
        isRecording = false;
        Log.d(TAG, "IMU recording stopped.");
    }

    // 初始化 CSV 文件和 CsvMapWriter
    private void initCSVFile() {
        File file = new File(filePath);
        //Log.d(TAG, "initCSVFile: filePath:"+filePath);
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            csvWriter = new CsvMapWriter(new FileWriter(file), CsvPreference.STANDARD_PREFERENCE);
            csvWriter.writeHeader(HEADER);
            Log.d(TAG, "CSV file initialized: " + filePath);
        } catch (IOException e) {
            Log.e(TAG, "initCSVFile error: " + e.getMessage());
        }
    }

    // 将当前数据写入 CSV 文件
    private void writeCSVLine(long timestamp, IMUData data) {
        if (csvWriter == null) return;
        try {
            SimpleDateFormat formatter = new SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault());
            Map<String, String> map = new HashMap<>();
            map.put(HEADER[0], formatter.format(new Date(timestamp)));
            map.put(HEADER[1], String.valueOf(data.accX));
            map.put(HEADER[2], String.valueOf(data.accY));
            map.put(HEADER[3], String.valueOf(data.accZ));
            map.put(HEADER[4], String.valueOf(data.gyroX));
            map.put(HEADER[5], String.valueOf(data.gyroY));
            map.put(HEADER[6], String.valueOf(data.gyroZ));
            csvWriter.write(map, HEADER);
        } catch (Exception e) {
            Log.e(TAG, "writeCSVLine error: " + e.getMessage());
        }
    }

    // 关闭 CSV 写入器
    private void closeCSVWriter() {
        if (csvWriter != null) {
            try {
                csvWriter.close();
            } catch (IOException e) {
                Log.e(TAG, "closeCSVWriter error: " + e.getMessage());
            }
        }
    }

    // SensorEventListener，处理加速度和陀螺仪数据
    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            long timestamp = System.currentTimeMillis();
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                currentData.accX = event.values[0];
                currentData.accY = event.values[1];
                currentData.accZ = event.values[2];
            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                currentData.gyroX = event.values[0];
                currentData.gyroY = event.values[1];
                currentData.gyroZ = event.values[2];
            }
            // 每次 sensor 更新都写入一行 CSV 数据
            writeCSVLine(timestamp, currentData);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // 可根据需要处理精度变化
        }
    };

    // 内部数据类，用于存储当前传感器数据
    public static class IMUData {
        public float accX, accY, accZ;
        public float gyroX, gyroY, gyroZ;

        public IMUData() {
            accX = accY = accZ = 0;
            gyroX = gyroY = gyroZ = 0;
        }
    }
}
