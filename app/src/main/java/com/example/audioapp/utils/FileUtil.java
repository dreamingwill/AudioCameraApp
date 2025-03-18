package com.example.audioapp.utils;

import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FileUtil {
    private static String rootPath = "";
    private static String basePath = Environment.getExternalStorageDirectory().getAbsolutePath();

    //原始文件(不能播放)
    private final static String AUDIO_PCM_BASEPATH = "/" + rootPath + "/pcm/";
    //可播放的高质量音频文件
    private final static String AUDIO_WAV_BASEPATH = "/" + rootPath + "/wav/";

    private final static String CSV_BASEPATH = "/" + rootPath + "/csv/";

    private final static String VIDEO_BASEPATH = "/" + rootPath + "/video/";

    private final static String SCREEN_BASEPATH = "/" + rootPath + "/screen/";
    private static void setRootPath(String rootPath) {
        FileUtil.rootPath = rootPath;
    }

    public static void setBasePath(String basePath)
    {
        FileUtil.basePath = basePath;
    }

    public static String getPcmFileAbsolutePath(String fileName) {
        return getFileAbsolutePath("pcm", fileName);
    }
    public static String getVideoFileAbsolutePath(String fileName) {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/Movies/" + fileName + ".mp4";
        //return getFileAbsolutePath("mp4", fileName);
    }

    public static String getScreenVideoFileAbsolutePath(String fileName) {
        String fileBasePath = basePath + SCREEN_BASEPATH;
        File file = new File(fileBasePath);
        //创建目录
        if (!file.exists()) {
            file.mkdirs();
        }
        return fileBasePath + fileName;
    }

    public static String createFilename(){
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    }

    public static String getCSVFileAbsolutePath(String fileName)
    {
        return getFileAbsolutePath("csv", fileName);
    }

    private static String getFileAbsolutePath(String type, String fileName)
    {
        if (fileName == null) {
            throw new NullPointerException("fileName can't be null");
        }
        if (!isSdcardExit()) {
            throw new IllegalStateException("sd card no found");
        }

        String mAudioWavPath = "";
        if (isSdcardExit()) {
            File videoFolder = new File(basePath + VIDEO_BASEPATH);
            //创建目录 video folder
            if (!videoFolder.exists()) {
                videoFolder.mkdirs();
            }
            if (!fileName.endsWith("." + type)) {
                fileName = fileName + '.' + type;
            }
            String fileBasePath = basePath + getBasePathByType(type);
            File file = new File(fileBasePath);
            //创建目录
            if (!file.exists()) {
                file.mkdirs();
            }
            mAudioWavPath = fileBasePath + fileName;
        }
        return mAudioWavPath;
    }

    private static String getBasePathByType(String type)
    {
        if (type.equals("wav")) {
            return AUDIO_WAV_BASEPATH;
        } else if (type.equals("pcm")) {
            return AUDIO_PCM_BASEPATH;
        } else if (type.equals("csv")){
            return CSV_BASEPATH;
        } else{
            return VIDEO_BASEPATH;
    }


    }

    public static String getWavFileAbsolutePath(String fileName) {
        return getFileAbsolutePath("wav", fileName);
    }

    /**
     * 判断是否有外部存储设备sdcard
     *
     * @return true | false
     */
    public static boolean isSdcardExit() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            return true;
        else
            return false;
    }

    /**
     * 获取全部pcm文件列表
     *
     * @return
     */
    public static List<File> getPcmFiles() {
        List<File> list = new ArrayList<>();
        String fileBasePath = basePath + AUDIO_PCM_BASEPATH;

        File rootFile = new File(fileBasePath);
        if (!rootFile.exists()) {
        } else {

            File[] files = rootFile.listFiles();
            for (File file : files) {
                list.add(file);
            }

        }
        return list;
    }

    /**
     * 获取全部wav文件列表
     *
     * @return
     */
    public static List<File> getWavFiles() {
        List<File> list = new ArrayList<>();
        String fileBasePath = basePath + AUDIO_WAV_BASEPATH;

        File rootFile = new File(fileBasePath);
        if (!rootFile.exists()) {
        } else {
            File[] files = rootFile.listFiles();
            for (File file : files) {
                list.add(file);
            }

        }
        return list;
    }

    /**
     * 获取全部wav文件列表 string array
     *
     * @return
     */
    public static String[] getWavFilesStrings() {
        List<File> list = new ArrayList<>();
        String fileBasePath = basePath + AUDIO_WAV_BASEPATH;

        File rootFile = new File(fileBasePath);
        if (!rootFile.exists()) {
        } else {
            File[] files = rootFile.listFiles();
            for (File file : files) {
                list.add(file);
            }

        }
        String [] filestrings = new String[list.size()];
        for(int i = 0; i < list.size(); i++){
            filestrings[i] = ""+list.get(i);
        }
        for(String s : filestrings){
            Log.d("wavtostring", "getWavFiles: " + s);
        }
        return filestrings;
    }

    /**
     * 获取全部video文件列表
     *
     * @return
     */
    public static List<File> getVideoFiles() {
        List<File> list = new ArrayList<>();
        String fileBasePath = basePath + VIDEO_BASEPATH;
        Log.d("getvideo", "getVideoFiles: ");
        File rootFile = new File(fileBasePath);
        if (!rootFile.exists()) {
        } else {
            File[] files = rootFile.listFiles();
            for (File file : files) {
                list.add(file);
            }

        }
        return list;
    }

    /**
     * 获取全部video文件列表 string array
     *
     * @return
     */
    public static String[] getVideoFilesStrings() {
        List<File> list = new ArrayList<>();
        String fileBasePath = basePath + VIDEO_BASEPATH;

        File rootFile = new File(fileBasePath);
        if (!rootFile.exists()) {
        } else {
            File[] files = rootFile.listFiles();
            for (File file : files) {
                list.add(file);
            }

        }
        String [] filestrings = new String[list.size()];
        for(int i = 0; i < list.size(); i++){
            filestrings[i] = ""+list.get(i);
        }
        for(String s : filestrings){
            Log.d("videotostring", "getVideoFiles: " + s);
        }
        return filestrings;
    }

    public static List<File> getCSVFiles() {
        List<File> list = new ArrayList<>();
        String fileBasePath = basePath + CSV_BASEPATH;

        File rootFile = new File(fileBasePath);
        if (!rootFile.exists()) {
        } else {
            File[] files = rootFile.listFiles();
            for (File file : files) {
                list.add(file);
            }

        }
        return list;
    }
}
