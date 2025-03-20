package com.example.audioapp.utils;

import java.util.Random;

public class BasicReplyHelper {
    private static final String[] REPLIES = {
            "没关系，每一次挑战都是成长的机会。",
            "游戏是享受的过程，输赢并不代表一切。",
            "你的努力值得肯定，继续加油！",
            "每个人都会遇到瓶颈，相信自己能突破。",
            "失败只是暂时的，调整一下状态继续前进！",
            "每次经历都是积累，今天的努力会有回报。",
            "别太苛责自己，享受过程比结果更重要。",
            "你的坚持会让你变得更强大！",
            "失误很正常，没人是完美的，继续努力！",
            "情绪低落时，深呼吸，给自己一个鼓励。",
            "游戏是放松的，别忘了享受其中的乐趣。",
            "即使暂时落后，也还有很多机会翻盘。",
            "没有谁能一直赢，但每个人都能一直进步。",
            "稳住心态，相信自己能做得更好。",
            "重要的不是这次结果，而是你在不断进步。",
            "你已经做得很好了，给自己一点肯定。",
            "保持自信，每一次尝试都在积累经验。",
            "相信你的实力，每一场都是成长的机会。",
            "每个高手都从失败中走来，继续前进吧！",
            "勇敢面对挑战，你比自己想象的更强大！"
    };


    private static final Random random = new Random();

    public static String getRandomReply() {
        int index = random.nextInt(REPLIES.length);
        return REPLIES[index];
    }
}
