package com.example.audioapp;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.audioapp.utils.PreferenceHelper;

public class SettingsFragment extends Fragment {

    private RadioGroup replyModeGroup;
    private RadioButton basicReplyButton, gptReplyButton, cButton;

    private RadioGroup radioGroupGameType;
    private RadioButton rbWzry, rbJc, rbShooter, rbRun, rbOthers;
    private Button btnConfirm;

    // SharedPreferences 文件名和键
    private static final String PREFS_NAME = "emo_preferences";
    private static final String KEY_GAME_TYPE = "game_type";
    public static final boolean DEAD_CHOICE = true;// 把模型选择写 死
    public static final boolean SMART_REPLY = true; //若写死，规定时候智能回复;若不写死，它就没用了



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        replyModeGroup = view.findViewById(R.id.reply_mode_group);
        basicReplyButton = view.findViewById(R.id.radio_basic_reply);
        gptReplyButton = view.findViewById(R.id.radio_gpt_reply);
        cButton = view.findViewById(R.id.c_button);


        radioGroupGameType = view.findViewById(R.id.radioGroupGameType);
        rbWzry = view.findViewById(R.id.radio_wzry);
        rbJc = view.findViewById(R.id.radio_jc);
        rbShooter = view.findViewById(R.id.radio_shooter);
        rbRun = view.findViewById(R.id.radio_run);
        rbOthers = view.findViewById(R.id.radio_others);
        btnConfirm = view.findViewById(R.id.btn_confirm);


        if(DEAD_CHOICE){
            // 根据 SMART_REPLY 变量设置默认选中项，并禁用修改
            boolean useBasicReply = !SMART_REPLY;
            basicReplyButton.setChecked(useBasicReply);
            gptReplyButton.setChecked(!useBasicReply);
            cButton.setChecked(false);
            // 禁用修改选项
            replyModeGroup.setEnabled(false);
            basicReplyButton.setEnabled(false);
            gptReplyButton.setEnabled(false);
            cButton.setEnabled(false);
        }else {
            // 可自由选择，但会锁定和解锁
            // 如果已经锁定，则禁用 RadioGroup 修改
            if (PreferenceHelper.isReplyLocked(requireContext())) {
                replyModeGroup.setEnabled(false);
                basicReplyButton.setEnabled(false);
                gptReplyButton.setEnabled(false);
                cButton.setEnabled(false);
            } else {
                replyModeGroup.setEnabled(true);
                basicReplyButton.setEnabled(true);
                gptReplyButton.setEnabled(true);
                cButton.setEnabled(true);
            }

            // 初始化选中状态
            int savedMode = PreferenceHelper.getReplyMode(requireContext());
            switch (savedMode) {
                case 1:
                    basicReplyButton.setChecked(true);
                    break;
                case 2:
                    gptReplyButton.setChecked(true);
                    break;
                case 3:
                    cButton.setChecked(true);
                    break;
                default:
                    basicReplyButton.setChecked(true);
            }
            replyModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
                int mode = 1; // 默认 A
                if (checkedId == R.id.radio_basic_reply) {
                    mode = 1; // A 模式
                } else if (checkedId == R.id.radio_gpt_reply) {
                    mode = 2; // B 模式
                } else if (checkedId == R.id.c_button) {
                    mode = 3; // C 模式
                }
                PreferenceHelper.setReplyMode(requireContext(), mode);
                //Toast.makeText(requireContext(), "已设置模式 " + mode, Toast.LENGTH_SHORT).show();
            });
        }






        // 从 SharedPreferences 读取已有的设置，默认为 1（王者荣耀）
        int savedGameType = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_GAME_TYPE, 1);
        switch (savedGameType) {
            case 1:
                rbWzry.setChecked(true);
                break;
            case 2:
                rbJc.setChecked(true);
                break;
            case 3:
                rbShooter.setChecked(true);
                break;
            case 4:
                rbRun.setChecked(true);
                break;
            case 5:
                rbOthers.setChecked(true);
                break;
            default:
                rbWzry.setChecked(true);
        }

        // 假设用户在设置界面选择并锁定后，点击一个“确定”按钮，完成设置


        btnConfirm.setOnClickListener(v -> {

            int selectedGameType = 1;
            boolean isLandscape = false;
            int selectedId = radioGroupGameType.getCheckedRadioButtonId();
            if (selectedId == R.id.radio_wzry) {
                selectedGameType = 1;
                isLandscape = true;
            } else if (selectedId == R.id.radio_jc) {
                selectedGameType = 2;
                isLandscape = true;
            } else if (selectedId == R.id.radio_shooter) {
                selectedGameType = 3;
                isLandscape = true;
            } else if (selectedId == R.id.radio_run) {
                selectedGameType = 4;
                isLandscape = false;
            }else if(selectedId == R.id.radio_others){
                selectedGameType = 5;
                isLandscape = false;
            }
            // 保存用户选择到 SharedPreferences
            requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putInt(KEY_GAME_TYPE, selectedGameType)
                    .putBoolean("is_landscape", isLandscape)
                    .putBoolean("reply_locked", true)
                    .apply();
            //Toast.makeText(requireContext(), "设置已保存", Toast.LENGTH_SHORT).show();

            // 可以在这里强制将 KEY_REPLY_LOCKED 设置为 true
            // 保存选择及锁定操作已经在设置中处理，此处直接导航到 SecondFragment
            NavController navController = Navigation.findNavController(view);
            navController.navigate(R.id.SecondFragment);
        });

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

    }
}
