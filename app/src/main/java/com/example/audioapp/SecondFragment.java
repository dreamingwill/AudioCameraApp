package com.example.audioapp;

import com.example.audioapp.services.EmotionMonitoringService;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.audioapp.services.EmotionMonitoringService;
import com.example.audioapp.utils.ChatGptHelper;
import com.example.audioapp.utils.FileUtil;
import com.example.audioapp.utils.IMURecorder;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SecondFragment extends Fragment {
    public static final String TAG = "SecondFragment";

    private boolean isLandscape = false, isMonitoring = false;
    private Button monitorButton, GptTestButton;
    private MediaProjectionManager mediaProjectionManager;


    private ActivityResultLauncher<Intent> monitoringLauncher;
    private IMURecorder imuRecorder;



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // 加载布局文件 fragment_second.xml
        View view = inflater.inflate(R.layout.fragment_second, container, false);

        // 获取按钮
        Button toggleOrientationButton = view.findViewById(R.id.btn_toggle_orientation);


        // 点击按钮切换屏幕方向
        toggleOrientationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (getActivity() != null) {
                    if (isLandscape) {
                        // 切换为竖屏
                        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    } else {
                        // 切换为横屏
                        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    }
                    isLandscape = !isLandscape;
                }
            }
        });


        return view;
    }
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FirstFragment.verifyPermissions(getActivity());

        monitorButton = view.findViewById(R.id.btn_monitor);
        // 根据持久化的状态更新按钮文字
        isMonitoring = getMonitoringState();
        if (isMonitoring) {
            monitorButton.setText("stop monitoring");
        } else {
            monitorButton.setText("monitor");
        }

        GptTestButton = view.findViewById(R.id.btn_Gpt);
        mediaProjectionManager = (MediaProjectionManager) requireActivity().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        Toast toast = new Toast(requireContext());
        View layout = LayoutInflater.from(requireContext()).inflate(R.layout.toast_layout, null);
        TextView text = layout.findViewById(R.id.toast_text);

        imuRecorder = new IMURecorder(requireContext());
        // 新建一个用于监测的 launcher
        monitoringLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Log.d(TAG, "monitoringLauncher: received screen capture result");
                        Intent data = result.getData();

                        // 启动 EmotionMonitoringService，并传入权限数据
                        Intent serviceIntent = new Intent(getContext(), EmotionMonitoringService.class);
                        serviceIntent.putExtra("resultCode", result.getResultCode());
                        serviceIntent.putExtra("resultData", data);
                        requireContext().startService(serviceIntent);
                        // 修改界面
                        setMonitoringState(true);  // 保存状态
                        isMonitoring = true;
                        monitorButton.setText("stop monitoring");
                        // IMU
                        String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                        File csvDir = requireContext().getApplicationContext().getExternalFilesDir("imu");
                        if (csvDir != null && !csvDir.exists()) {
                            csvDir.mkdirs();
                        }
                        String filePath = new File(csvDir, fileName + ".csv").getAbsolutePath();
                        imuRecorder.startRecording(filePath);

                        Log.d(TAG, "Monitoring service started: " + serviceIntent.toString());
                    } else {
                        Toast.makeText(getContext(), "屏幕录制权限未授权", Toast.LENGTH_SHORT).show();
                    }
                });


        monitorButton.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View view) {
                if (!isMonitoring) {

                    // 使用 monitoringLauncher 启动屏幕录制权限请求，后续回调中会启动 EmotionMonitoringService
                    Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
                    monitoringLauncher.launch(captureIntent);

                    // 此处 isMonitoring 状态可在回调中设置，或者你也可以在点击后就设置为 true

                } else {
                    // 停止监测
                    Intent serviceIntent = new Intent(getContext(), EmotionMonitoringService.class);
                    requireContext().stopService(serviceIntent);
                    //
                    //closeCSVWriter();
                    setMonitoringState(false); // 保存状态
                    monitorButton.setText("monitor");
                    isMonitoring = false;
                    imuRecorder.stopRecording();
                }
            }
        });

        GptTestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // ChatGpt
                ChatGptHelper chatGptHelper = new ChatGptHelper();

                chatGptHelper.getInterventionResponse(
                        EmotionMonitoringService.captureBuffer,
                        new ChatGptHelper.ChatGptCallback() {
                            @Override
                            public void onSuccess(String reply) {

                                // 在主线程中更新 UI，可使用 Handler 切换
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    Toast toast = Toast.makeText(requireContext(), reply, Toast.LENGTH_LONG);
                                    toast.show();                                    //showLongDurationToast(requireContext(),reply,10_000);

//                                    text.setText(reply);
//                                    toast.setView(layout);
//                                    toast.setDuration(Toast.LENGTH_LONG); // 虽然系统限制，但部分设备可能忽略
//
//                                    toast.show();
//                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                                        toast.cancel(); // 强制关闭 Toast
//                                    }, 10000); // 10 秒后关闭
                                    Log.d(TAG, "onSuccess: "+reply);
                                    // 额外延长显示时间
                                    new Handler(Looper.getMainLooper()).postDelayed(toast::show, 1000); // 额外延长 3.5 秒
                                });
                            }
                            @Override
                            public void onFailure(String error) {
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    //showCustomToast(error,5000);
                                    Toast.makeText(requireContext(), "Failed to get response: " + error, Toast.LENGTH_SHORT).show();
                                    Log.e(TAG, "onFailure: Failed to get response:" + error );
                                });
                            }
                        }
                );

            }
        });
    }
    public static void showLongDurationToast(final Context context, final String message, final int durationInMillis) {
        final Toast toast = Toast.makeText(context, message, Toast.LENGTH_LONG);
        // 默认 Toast.LENGTH_LONG 大约显示 3500 毫秒
        final int toastDurationInMilliSeconds = 3000;
        final long startTime = System.currentTimeMillis();
        final Handler handler = new Handler(Looper.getMainLooper());

        // 使用 Runnable 反复显示 Toast
        Runnable showToastRunnable = new Runnable() {
            @Override
            public void run() {
                toast.show();
                if (System.currentTimeMillis() - startTime < durationInMillis) {
                    // 延迟一定时间后再次显示
                    handler.postDelayed(this, toastDurationInMilliSeconds);
                }
            }
        };
        handler.post(showToastRunnable);
    }

    private void setMonitoringState(boolean isMonitoring) {
        SharedPreferences prefs = requireContext().getSharedPreferences("monitor_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("isMonitoring", isMonitoring).apply();
    }

    private boolean getMonitoringState() {
        SharedPreferences prefs = requireContext().getSharedPreferences("monitor_prefs", Context.MODE_PRIVATE);
        return prefs.getBoolean("isMonitoring", false);
    }

}

