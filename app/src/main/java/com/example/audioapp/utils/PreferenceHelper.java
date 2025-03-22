package com.example.audioapp.utils;

import static com.example.audioapp.SettingsFragment.DEAD_CHOICE;
import static com.example.audioapp.SettingsFragment.SMART_REPLY;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceHelper {
    private static final String PREF_NAME = "emo_preferences";
    private static final String KEY_REPLY_MODE = "reply_mode";  // A=1, B=2, C=3
    private static final String KEY_REPLY_LOCKED = "reply_locked";

    // 保存用户选择的回复模式，并锁定选择
    public static void setReplyMode(Context context, int  mode) {
        if (DEAD_CHOICE){
            return;
        }
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt(KEY_REPLY_MODE, mode);
        // 锁定回复模式（当前会话或永久，视你的需求而定）
        editor.putBoolean(KEY_REPLY_LOCKED, true);
        editor.apply();
    }

    // 获取用户选择的回复模式，默认 true


    public static int getReplyMode(Context context) {
        if(DEAD_CHOICE) {
            return 2;
        }else {
            SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            return sp.getInt(KEY_REPLY_MODE, 1);
        }
    }

    // 判断回复模式是否已被锁定
    public static boolean isReplyLocked(Context context) {
        if(DEAD_CHOICE){
            return true;
        }
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_REPLY_LOCKED, false);
    }

    // 解锁回复模式，允许用户重新选择
    public static void unlockReplyMode(Context context) {
        if(DEAD_CHOICE){
            return;
        }
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putBoolean(KEY_REPLY_LOCKED, false).apply();
    }
}

