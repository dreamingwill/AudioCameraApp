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
    private RadioButton basicReplyButton, gptReplyButton;

    private RadioGroup radioGroupGameType;
    private RadioButton rbWzry, rbJc, rbShooter, rbOthers;
    private Button btnConfirm;

    // SharedPreferences 文件名和键
    private static final String PREFS_NAME = "emo_preferences";
    private static final String KEY_GAME_TYPE = "game_type";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        replyModeGroup = view.findViewById(R.id.reply_mode_group);
        basicReplyButton = view.findViewById(R.id.radio_basic_reply);
        gptReplyButton = view.findViewById(R.id.radio_gpt_reply);


        radioGroupGameType = view.findViewById(R.id.radioGroupGameType);
        rbWzry = view.findViewById(R.id.radio_wzry);
        rbJc = view.findViewById(R.id.radio_jc);
        rbShooter = view.findViewById(R.id.radio_shooter);
        rbOthers = view.findViewById(R.id.radio_others);
        btnConfirm = view.findViewById(R.id.btn_confirm);

        // 如果已经锁定，则禁用 RadioGroup 修改
        if (PreferenceHelper.isReplyLocked(requireContext())) {
            replyModeGroup.setEnabled(false);
            basicReplyButton.setEnabled(false);
            gptReplyButton.setEnabled(false);
            //Toast.makeText(requireContext(), "回复模式已锁定，无法修改", Toast.LENGTH_SHORT).show();
        }

        replyModeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                boolean useBasicReply = true;
                if (checkedId == R.id.radio_basic_reply) {
                    useBasicReply = true;
                } else if (checkedId == R.id.radio_gpt_reply) {
                    useBasicReply = false;
                } else {
                    return;
                }
                // 保存用户选择，并锁定设置
                PreferenceHelper.setReplyMode(requireContext(), useBasicReply);
                //Toast.makeText(requireContext(), "回复模式已设置，且锁定", Toast.LENGTH_SHORT).show();
            }
        });



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
            } else if (selectedId == R.id.radio_others) {
                selectedGameType = 4;
                isLandscape = false;
            }
            // 保存用户选择到 SharedPreferences
            requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putInt(KEY_GAME_TYPE, selectedGameType)
                    .putBoolean("is_landscape", isLandscape)
                    .apply();
            Toast.makeText(requireContext(), "设置已保存", Toast.LENGTH_SHORT).show();

            // 保存选择及锁定操作已经在设置中处理，此处直接导航到 SecondFragment
            NavController navController = Navigation.findNavController(view);
            navController.navigate(R.id.SecondFragment);
        });

        return view;
    }
}
