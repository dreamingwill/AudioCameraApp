package com.example.audioapp.utils;

import java.util.Random;

public class BasicReplyHelper {
    private static final String[] REPLIES = {
            "补刀不准没关系,你的价值不在发育上",
            "团战失误很正常,下次记得跟着节奏走",
            "装备落后不要紧,运营节奏才是关键",
            "技能空了别着急,关键时刻还能留着",
            "别气馁，每次失败都是成长的机会",
            "放松一下，调整状态，下次会更好",
            "多练习，你一定能突破瓶颈",
            "遇到挫折是正常的，继续努力吧",
            "不要灰心，胜利总在不远处",
            "放平心态，享受游戏的乐趣",
            "失败只是暂时的，相信你会成功",
            "试着换个角度思考，突破困境",
            "调整节奏，马上就会更好",
            "保持耐心，胜利属于坚持的人",
            "每个高手都有低谷，继续加油",
            "别忘了休息，状态调整后更强大",
            "机会总会留给有准备的人",
            "逆境中成长，失败是成功之母",
            "相信自己的实力，未来可期",
            "努力过后，结果自然会不同"
    };

    private static final Random random = new Random();

    public static String getRandomReply() {
        int index = random.nextInt(REPLIES.length);
        return REPLIES[index];
    }
}
