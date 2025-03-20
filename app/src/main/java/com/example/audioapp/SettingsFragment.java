package com.example.audioapp;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        replyModeGroup = view.findViewById(R.id.reply_mode_group);
        basicReplyButton = view.findViewById(R.id.radio_basic_reply);
        gptReplyButton = view.findViewById(R.id.radio_gpt_reply);

        // 如果已经锁定，则禁用 RadioGroup 修改
        if (PreferenceHelper.isReplyLocked(requireContext())) {
            replyModeGroup.setEnabled(false);
            basicReplyButton.setEnabled(false);
            gptReplyButton.setEnabled(false);
            Toast.makeText(requireContext(), "回复模式已锁定，无法修改", Toast.LENGTH_SHORT).show();
        }

        replyModeGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                boolean useBasicReply;
                if (checkedId == R.id.radio_basic_reply) {
                    useBasicReply = true;
                } else if (checkedId == R.id.radio_gpt_reply) {
                    useBasicReply = false;
                } else {
                    return;
                }
                // 保存用户选择，并锁定设置
                PreferenceHelper.setReplyMode(requireContext(), useBasicReply);
                Toast.makeText(requireContext(), "回复模式已设置，且锁定", Toast.LENGTH_SHORT).show();
            }
        });

        // 假设用户在设置界面选择并锁定后，点击一个“确定”按钮，完成设置
        Button btnConfirm = view.findViewById(R.id.btn_confirm);
        btnConfirm.setOnClickListener(v -> {
            // 保存选择及锁定操作已经在设置中处理，此处直接导航到 SecondFragment
            NavController navController = Navigation.findNavController(view);
            navController.navigate(R.id.SecondFragment);
        });

        return view;
    }
}
