package com.example.audioapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Handler;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import android.graphics.Bitmap;


import com.example.audioapp.entity.SensorData;
import com.example.audioapp.services.EmotionMonitoringService;
import com.example.audioapp.services.MediaPlayerService;
import com.example.audioapp.services.ScreenRecordingService;
import com.example.audioapp.services.VideoRecordingService;
import com.example.audioapp.utils.AudioRecorder;
import com.example.audioapp.utils.FaceDetectorHelper;
import com.example.audioapp.utils.FileUtil;

import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.io.ICsvMapWriter;
import org.supercsv.prefs.CsvPreference;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import androidx.camera.core.ImageCapture;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.core.content.ContextCompat;
import com.example.audioapp.databinding.FragmentFirstBinding;
import com.example.audioapp.utils.ModelLoader;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.core.Preview;
import androidx.camera.core.CameraSelector;

//@RequiresApi(api = Build.VERSION_CODES.KITKAT)
public class FirstFragment extends Fragment {
    //Motion
    private TextView motionText = null;

    //Acceleration sensor: TYPE_ACCELEROMETER: include gravity
    private SensorManager mSensorManager;
    private TextView mSensorText = null;
    private Sensor mSensor;
    private SensorData mData = new SensorData();

    //Gyroscope sensor: TYPE_GYROSCOPE
    private SensorManager mSensorManager2;
    private Sensor mSensor2;

    //CSV file to store data from accelerator and gyroscope
    //    private String[] HEADER = new String[] { "时间", "X方向加速度", "X方向加速度", "X方向加速度",
    //            "accuracy"};
    private String[] HEADER = new String[] { "Time", "X-acc", "Y-acc", "Z-acc", "Accuracy", "X-Rot", "Y-Rot", "Z-Rot"};
    private ICsvMapWriter beanWriter = null;

    //Recorder and player part
    private TextView phrases;
    private Button clickShow;
    private int phrase_type;
    private Button start,pause,play, stop, type, pause2, next, previous, loop, switch_btn, playSignalButton;
    private Button imageCaptureButton, videoCaptureButton, collectDataButton,frontRecordingButton,screenRecordButton, modelPredictButton, monitorButton;
    private TextView wavplaying;
    private ProgressBar progressBar;
    private PreviewView viewFinder = null;
    private AudioRecorder mAudioRecorder = null;
    MediaPlayer player = new MediaPlayer();
    private SurfaceView mvideo;
    private BroadcastReceiver screenRecordingStateReceiver;
    String[] musiclist;
    int listIndex;
    int listLength;
    private ModelLoader modelLoader;

    private int cycleCount = 0; // 周期计数器
    private Handler cycleHandler = new Handler();



    //camera code
    private FragmentFirstBinding binding;

    private ImageCapture imageCapture = null;

    private VideoCapture<Recorder> videoCapture = null;
    private Recording recording = null;// video recording

    private ExecutorService cameraExecutor;
    private boolean isRecordingVideo = false, isRecording_screen = false, isSignalPlaying = false; //frontRecording


    private static final String TAG = "FirstFragment";
    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS;
    //screen recording
    private static final int SCREEN_RECORD_REQUEST_CODE = 1000; // 录屏请求码
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1001;// 截屏请求码
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private MediaRecorder mediaRecorder;
    private VirtualDisplay virtualDisplay;
    private int screenDensity;
    // model thing
    Bitmap bitmap = null;

    AssetManager assetManager = null;
    private boolean isMonitoring = false;
    private ActivityResultLauncher<Intent> screenCaptureLauncher, monitoringLauncher;


    static {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            REQUIRED_PERMISSIONS = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        } else {
            REQUIRED_PERMISSIONS = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
            };
        }
    }


    // Sensor
    private SensorEventListener mListener =  new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
//            System.out.println("x=" + event.values[0]);
//            System.out.println("y=" + event.values[1]);
//            System.out.println("z=" + event.values[2]);
            mData.setX(event.values[0]);
            mData.setY(event.values[1]);
            mData.setZ(event.values[2]);
            updateSensorStateText();
            //Log.d("sensor", event.sensor.getName());
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            mData.setAccuracy(accuracy);
            updateSensorStateText();
        }
    };

    private SensorEventListener mListener2 =  new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            mData.setRotx(event.values[0]);
            mData.setRoty(event.values[1]);
            mData.setRotz(event.values[2]);
            updateSensorStateText();
            //Log.d("sensor", event.sensor.getName());
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            mData.setAccuracy(accuracy);
            updateSensorStateText();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        assetManager = requireActivity().getAssets();


        //motion
        //setOnTouchListener(new myOnTouchListener());

        //Acceleration sensor
        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        //Gyroscope sensor
        mSensorManager2 = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        mSensor2 = mSensorManager2.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        mSensorManager.registerListener(mListener, mSensor, 50_000);
        mSensorManager2.registerListener(mListener2, mSensor2, 50_000);


        // creating bitmap from packaged into app android asset 'image.jpg',
        // app/src/main/res/raw/image.jpg
        try {
            bitmap = BitmapFactory.decodeStream(assetManager.open("lc.png"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //bitmap = BitmapFactory.decodeStream(getResources().openRawResource(R.raw.anger_me));
        // loading serialized torchscript module from packaged into app android asset model.pt,
        // app/src/main/res/raw/
        // 加载模型
        modelLoader = new ModelLoader(assetManager);

    }

    private class myOnTouchListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int x = (int) event.getX();
            int y = (int) event.getY();
            motionText.setText("Motion: " + event.getAction() + ", " + event.getPressure()
            +"\nx:"+x+"y:"+y);
            return true;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: ");
        if (isRecording_screen){
            stopScreenRecording();
        }

        if (isRecordingVideo) {
            // 停止录制
            Intent serviceIntent = new Intent(getContext(), VideoRecordingService.class);
            requireActivity().stopService(serviceIntent);
            // 音频
            stopAudioRecording();
        }

//        if(isMonitoring){
//            Intent serviceIntent = new Intent(getContext(), EmotionMonitoringService.class);
//            requireContext().stopService(serviceIntent);
//            //
//            closeCSVWriter();
//        }

        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            Log.d("cameraExecutor", "cameraExecutor destroyed");
        }

        mSensorManager.unregisterListener(mListener);
        mSensorManager2.unregisterListener(mListener2);
        Log.d("sensor", "sensor destroyed");
    }


    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        //camera
        //binding = FragmentFirstBinding.inflate(inflater, container, false);

        return inflater.inflate(R.layout.fragment_first, container, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: ");
        IntentFilter filter = new IntentFilter(ScreenRecordingService.ACTION_UPDATE_RECORDING_STATE);
        getActivity().registerReceiver(screenRecordingStateReceiver, filter);
    }

    /*
    @Override
    public void onResume() {
        super.onResume();
        Log.e("111", "onResume");
        if (null != mSensor) {
            Log.e("111", "onResume register");
            //mSensorManager.registerListener(mListener, mSensor, SensorManager.SENSOR_DELAY_GAME);
            mSensorManager.registerListener(mListener, mSensor, 50000);
        }
        if(null != mSensor2) {
            //mSensorManager2.registerListener(mListener2, mSensor2, SensorManager.SENSOR_DELAY_GAME);
            mSensorManager2.registerListener(mListener2, mSensor2, 50000);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.e("111", "onPause");
        if (null != mSensor) {
            Log.e("111", "onPause unregister");
            mSensorManager.unregisterListener(mListener);
        }
        if (null != mSensor2) {
            mSensorManager.unregisterListener(mListener2);
        }
    }
    */
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: ");
        // 注册广播接收器
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.ACTION_VIDEO_RECORDING_STARTED");
        filter.addAction("com.example.ACTION_VIDEO_RECORDING_STOPPED");
        //getActivity().registerReceiver(videoRecordingReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: ");
        // 解除注册广播接收器
        //requireActivity().unregisterReceiver(videoRecordingReceiver);
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: ");
        requireActivity().unregisterReceiver(screenRecordingStateReceiver);
    }

    private void updateSensorStateText()
    {
        if (null != mSensorText) {
            mSensorText.setText(mData.getText());
            try{
                writeWithCsvBeanWriter();
            }catch (Exception exception) {

            }
        }
    }

    private static CellProcessor[] getProcessors() {
        final CellProcessor[] processors = new CellProcessor[] {
                new NotNull(), // 时间
                new NotNull(), // x加速度
                new NotNull(), // y加速度
                new NotNull(), // z加速度
                new NotNull(), // accuracy
                new NotNull(), //x rotation
                new NotNull(), //y rotation
                new NotNull()  //z rotation
        };

        return processors;
    }

    private void initCSVFile(String fileName)
    {
        //File file = new File(fileName +".csv");
        File file = new File(fileName);
        try{
            if (!file.exists()) {
                file.createNewFile();
            }
            final CellProcessor[] processors = getProcessors();

            // write the header
            //beanWriter = new CsvMapWriter(new FileWriter(fileName +".csv"),
            beanWriter = new CsvMapWriter(new FileWriter(fileName),
                    CsvPreference.STANDARD_PREFERENCE);
            beanWriter.writeHeader(HEADER);
        }catch (IOException exception) {

        }
    }

    private void writeWithCsvBeanWriter() throws Exception {
        try {
            final CellProcessor[] processors = getProcessors();
            Map<String,String> map = new HashMap<>();
            SimpleDateFormat formater = new SimpleDateFormat(FILENAME_FORMAT);
            map.put(HEADER[0],formater.format(new Date()));
            map.put(HEADER[1], String.valueOf(mData.getX()));
            map.put(HEADER[2], String.valueOf(mData.getY()));
            map.put(HEADER[3], String.valueOf(mData.getZ()));
            map.put(HEADER[4], String.valueOf(mData.getAccuracy()));
            map.put(HEADER[5], String.valueOf(mData.getRotx()));
            map.put(HEADER[6], String.valueOf(mData.getRoty()));
            map.put(HEADER[7], String.valueOf(mData.getRotz()));
            beanWriter.write(map, HEADER, processors);
        }
        finally {
        }
    }

    private void closeCSVWriter() {
        if( beanWriter != null ) {
            try{
                beanWriter.close();
            }catch (Exception exception) {

            }
        }
    }

    @SuppressLint("SetTextI18n")
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Log.e(TAG, "onViewCreated:");


        FileUtil.setBasePath(getActivity().getExternalFilesDir(null).toString());
        view.setOnTouchListener(new myOnTouchListener());

//        //Acceleration sensor
//        mSensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
//        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//
//        //Gyroscope sensor
//        mSensorManager2 = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
//        mSensor2 = mSensorManager2.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        motionText = view.findViewById(R.id.textView2);
        mSensorText = view.findViewById(R.id.textview_first);
        mAudioRecorder = AudioRecorder.getInstance();
        start = view.findViewById(R.id.start);
        pause = view.findViewById(R.id.pause);
        pause.setEnabled(false);
        play = view.findViewById(R.id.play);
        playSignalButton = view.findViewById(R.id.play_signal_button);
        stop = view.findViewById(R.id.stop);
        stop.setEnabled(false);
        phrases = view.findViewById(R.id.phrases);
        clickShow = view.findViewById(R.id.click_display);
        phrase_type = 0;
        wavplaying = view.findViewById(R.id.textView);
        mvideo = view.findViewById(R.id.surfaceView);
        type = view.findViewById(R.id.type);
        loop = view.findViewById(R.id.loop);
        pause2 = view.findViewById(R.id.pause2);
        pause2.setEnabled(false);
        next = view.findViewById(R.id.next);
        previous = view.findViewById(R.id.previous);
        viewFinder = view.findViewById(R.id.viewFinder);
        imageCaptureButton = view.findViewById(R.id.image_capture_button);
        videoCaptureButton = view.findViewById(R.id.video_capture_button);
        collectDataButton = view.findViewById(R.id.collect_data_button);
        //frontRecordingButton = view.findViewById(R.id.front_recording_button);
        progressBar = view.findViewById(R.id.progressBar);
        mvideo.getHolder().setKeepScreenOn(true);//保持屏幕常亮
        musiclist = FileUtil.getWavFilesStrings();
        listIndex = 0;
        listLength = musiclist.length;
        // 录屏
        mediaProjectionManager = (MediaProjectionManager) requireActivity().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        screenDensity = getResources().getDisplayMetrics().densityDpi;
        // 获取按钮引用
        screenRecordButton = view.findViewById(R.id.screen_record_button);
        modelPredictButton = view.findViewById(R.id.model_predict_button);
        monitorButton = view.findViewById(R.id.monitor_button);


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
                        isMonitoring = true;
                        monitorButton.setText("stop monitoring");
                        Log.d(TAG, "Monitoring service started: " + serviceIntent.toString());
                    } else {
                        Toast.makeText(getContext(), "屏幕录制权限未授权", Toast.LENGTH_SHORT).show();
                    }
                });

        // 初始化 ActivityResultLauncher
        screenCaptureLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                Log.d(TAG, "screenCaptureLauncher: ");
                Intent data = result.getData();

                // 启动前台服务并传递权限数据
                Intent serviceIntent = new Intent(getContext(), ScreenRecordingService.class);
                serviceIntent.putExtra("resultCode", result.getResultCode());
                serviceIntent.putExtra("data", data);
                requireContext().startService(serviceIntent);
                Log.d(TAG, serviceIntent.toString());
            }
        });
        // 设置按钮点击监听
        screenRecordButton.setOnClickListener(v -> {

            if (isRecording_screen) {
                // 停止录制
                stopScreenRecording();
            } else {
                // 开始录制
                startScreenRecording();
            }

        });
        screenRecordingStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
// 更新 UI 中按钮的文本
                isRecording_screen = intent.getBooleanExtra("isRecording", false);
                Log.d(TAG, "onReceive: isRecording_screen = " + isRecording_screen);
                screenRecordButton.setText(isRecording_screen ? "Stop scr rec" : "scr rec");

            }
        };
        
        previous.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listLength == 0) {
                    Toast.makeText(getContext(), "No files available.", Toast.LENGTH_SHORT).show();
                } else {
                    listIndex = (listIndex - 1 + listLength) % listLength;
                    play.performClick();
                    Toast.makeText(getContext(), "Previous", Toast.LENGTH_SHORT).show();
                }
            }
        });

        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listLength == 0) {
                    Toast.makeText(getContext(), "No files available.", Toast.LENGTH_SHORT).show();
                } else {
                    listIndex = (listIndex + 1) % listLength;
                    play.performClick();
                    Toast.makeText(getContext(), "Next", Toast.LENGTH_SHORT).show();
                }
            }
        });

        pause2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(pause2.getText().toString().equals("pause")){
                    player.pause();
                    pause2.setText("resume");
                    Toast.makeText(getContext(), "Pause", Toast.LENGTH_SHORT).show();
                } else {
                    player.start();
                    pause2.setText("pause");
                    Toast.makeText(getContext(), "Resume", Toast.LENGTH_SHORT).show();
                }
            }
        });

        clickShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(phrase_type == 0) {
                    phrases.setText("Can I help you?");
                } else if(phrase_type == 1) {
                    phrases.setText("What's your name?");
                } else if(phrase_type == 2) {
                    phrases.setText("Nice to meet you.");
                } else if(phrase_type == 3) {
                    phrases.setText("I need your help.");
                }
                phrase_type = (phrase_type + 1) % 4;
            }
        });

        type.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(type.getText().toString().equals(".wav")){
                    type.setText(".video");
                    musiclist = FileUtil.getVideoFilesStrings();
                    listLength = musiclist.length;
                    Log.d("file", "onClickvideo: "+ musiclist.length);
                    Toast.makeText(getContext(), "Videos selected", Toast.LENGTH_SHORT).show();
                } else {
                    type.setText(".wav");
                    musiclist = FileUtil.getWavFilesStrings();
                    listLength = musiclist.length;
                    Log.d("file", "onClickwav: "+ musiclist.length);
                    Toast.makeText(getContext(), "Wav selected", Toast.LENGTH_SHORT).show();
                }
            }
        });

        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listLength == 0) {
                    Toast.makeText(getContext(), "No files available.", Toast.LENGTH_SHORT).show();
                } else {
                    pause2.setEnabled(true);
                    pause2.setText("pause");
                    stop.setEnabled(true);
                    int index = musiclist[listIndex].lastIndexOf("/");
                    String fileName = musiclist[listIndex].substring(index + 1);
                    if (wavplaying == null || wavplaying.getText().toString().isEmpty()) {
                        wavplaying.setText("Playing: " + fileName);
                        try {
                            player.setDisplay(mvideo.getHolder());
                            player.setDataSource(musiclist[listIndex]);
                            Log.d("indexplaying", "onClick: " + listIndex);
                            player.prepare();
                            player.start();
//                            player.setLooping(true);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        player.stop();
                        player.reset();
                        wavplaying.setText("Playing: " + fileName);
                        try {
                            player.setDisplay(mvideo.getHolder());
                            player.setDataSource(musiclist[listIndex]);
                            Log.d("indexplaying", "onClick: " + listIndex);
                            player.prepare();
                            player.start();
//                            player.setLooping(true);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        playSignalButton.setOnClickListener(view1 -> {
            if (isSignalPlaying) {
                // 停止音频播放
                Intent serviceIntent = new Intent(getContext(), MediaPlayerService.class);
                requireContext().stopService(serviceIntent);
                playSignalButton.setText("signal");
                isSignalPlaying = false;
            } else {
                // 播放音频
                Intent serviceIntent = new Intent(getContext(), MediaPlayerService.class);
                requireContext().startService(serviceIntent);

                // 设置按钮文本为 "停止"
                playSignalButton.setText("stop");
                isSignalPlaying = true;
            }

        });
        loop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(loop.getText().toString().equals("loop off")){
                    loop.setText("loop on");
                    Toast.makeText(getContext(), "LOOP ON", Toast.LENGTH_SHORT).show();
                } else {
                    loop.setText("loop off");
                    Toast.makeText(getContext(), "LOOP OFF", Toast.LENGTH_SHORT).show();
                }
            }
        });

        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
//                Log.d("completion", "onCompletion: ");
                if(loop.getText().toString().equals("loop on")){
                    listIndex -= 1;
                }
                next.performClick();
            }
        });

        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(wavplaying.getText().toString().equals("Stopped") || wavplaying.getText().toString().isEmpty()){
                } else {
                    player.stop();
                    pause2.setEnabled(false);
                    pause2.setText("pause");
                    stop.setEnabled(false);
                    wavplaying.setText("Stopped");
                    Toast.makeText(getContext(), "Stop", Toast.LENGTH_SHORT).show();
                }
            }
        });

        start.setOnClickListener(new View.OnClickListener() {
//            @RequiresApi(api = Build.VERSION_CODES.KITKAT)
            @Override
            public void onClick(View view) {
                Log.d("encheck", "onclick: "+mAudioRecorder.getStatus());
                try {
                    if (mAudioRecorder.getStatus() == AudioRecorder.Status.STATUS_NO_READY) {
                        Log.d("encheck", "after click1: "+mAudioRecorder.getStatus());
                        pause.setEnabled(true);

                        // 获取当前时间的毫秒值
                        long currentTimeMillis = System.currentTimeMillis();
                        //初始化录音
                        String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(currentTimeMillis));
                        mAudioRecorder.createDefaultAudio(fileName);
                        mAudioRecorder.startRecord(null);
//                        start.setText("停止录音");
                        start.setText("Stop audio rec");
//                        pause.setVisibility(View.VISIBLE);
                        initCSVFile(FileUtil.getCSVFileAbsolutePath(fileName));
                        Log.d("encheck", "after click2: "+mAudioRecorder.getStatus());
                    } else {
                        Log.d("encheck", "stop1: "+mAudioRecorder.getStatus());
                        pause.setEnabled(false);
                        //停止录音
                        mAudioRecorder.stopRecord();
                        closeCSVWriter();
//                        start.setText("开始录音");
//                        pause.setText("暂停录音");
                        start.setText("Audio Rec");
                        pause.setText("Pause Audio Rec");
                        String msg = "AudioRecording success:" + mAudioRecorder.getFilename();
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
//                        pause.setVisibility(View.GONE);
                        Log.d("encheck", "stop2: "+mAudioRecorder.getStatus());
                    }
                } catch (IllegalStateException e) {
                    Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        view.findViewById(R.id.btn_first_list).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), ListActivity.class);
                intent.putExtra("type","wav");
                startActivity(intent);
            }
        });
        view.findViewById(R.id.btn_first_pcm).setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), ListActivity.class);
                intent.putExtra("type", "pcm");
                startActivity(intent);
            }
        });
        view.findViewById(R.id.btn_first_json).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), ListActivity.class);
                intent.putExtra("type", "csv");
                startActivity(intent);
            }
        });
        view.findViewById(R.id.btn_videolist).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), ListActivity.class);
                intent.putExtra("type", "mp4");
                startActivity(intent);
            }
        });
        pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d("encheck", "click pause: "+mAudioRecorder.getStatus());
                try {
                    if (mAudioRecorder.getStatus() == AudioRecorder.Status.STATUS_START) {
                        //暂停录音
                        mAudioRecorder.pauseRecord();
//                        pause.setText("继续录音");
                        pause.setText("Keep Recording");
                        Log.d("encheck", "after pause: "+mAudioRecorder.getStatus());
                    } else {
                        Log.d("encheck", "click keep: "+mAudioRecorder.getStatus());
                        mAudioRecorder.startRecord(null);
//                        pause.setText("暂停录音");
                        pause.setText("Pause Recording");
                        Log.d("encheck", "after keep: "+mAudioRecorder.getStatus());
                    }
                } catch (IllegalStateException e) {
                    Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });



        collectDataButton.setOnClickListener(v -> collectData(15));

        videoCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent serviceIntent = new Intent(getActivity(), VideoRecordingService.class);
                if (isRecordingVideo) {
                    // 停止录制
                    requireActivity().stopService(serviceIntent);
                    isRecordingVideo = false;
                    videoCaptureButton.setText("video rec");
                    // 音频
                    stopAudioRecording();
                    start.setEnabled(true);
                } else {
                    // 启动录制
                    requireActivity().startService(serviceIntent);
                    isRecordingVideo = true;
                    videoCaptureButton.setText("stop video rec");
                    // 音频
                    startAudioRecording();
                    start.setEnabled(false);
                }

            }
        });
        imageCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePhoto();
            }
        });

        modelPredictButton.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View view) {
                // 运行模型
                if (bitmap != null) {
                    long startTime = System.currentTimeMillis();

                    float[] output = modelLoader.runModel(modelLoader.getExampleBitmap());
                    long endTime = System.currentTimeMillis();
                    long duration = endTime - startTime;
                    // 处理输出结果
                    System.out.printf("模型 %s 的预测图片 %s 结果 -> a: %.4f, v: %.4f, 耗时: %d ms%n", ModelLoader.modelPath, ModelLoader.exampleImgPath, output[0], output[1], duration);
                    Log.d(TAG, "onClick: a:" + output[0] + " , v:" + output[1] + ", 耗时: " + duration + " ms");
                    motionText.setText("a: " + output[0] + " , v " + output[1] + ", \n耗时: " + duration + " ms");

                    FaceDetectorHelper.cropFaceFromBitmap(getContext(), modelLoader.getExampleBitmap(), new FaceDetectorHelper.FaceDetectionCallback() {
                        @Override
                        public void onFaceDetected(@Nullable Bitmap faceBitmap) {
                            if (faceBitmap == null) {
                                // 没有检测到人脸，则不进行模型推理
                                Log.d(TAG, "No face detected, skipping model inference.");
                            }else {
                                Log.d(TAG, "face detected, to do ...");
                            }
                        }});
                }



            }
        });
        monitorButton.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View view) {
                if (!isMonitoring) {
                    String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    // 使用 monitoringLauncher 启动屏幕录制权限请求，后续回调中会启动 EmotionMonitoringService
                    Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
                    monitoringLauncher.launch(captureIntent);
                    initCSVFile(FileUtil.getCSVFileAbsolutePath(fileName));
                    // 此处 isMonitoring 状态可在回调中设置，或者你也可以在点击后就设置为 true

                } else {
                    // 停止监测
                    Intent serviceIntent = new Intent(getContext(), EmotionMonitoringService.class);
                    requireContext().stopService(serviceIntent);
                    //
                    closeCSVWriter();
                    monitorButton.setText("monitor");
                    isMonitoring = false;
                }
            }
        });

        cameraExecutor = Executors.newSingleThreadExecutor();


        verifyPermissions(getActivity());
    }


    private void startScreenRecording() {
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        screenCaptureLauncher.launch(intent);  // 使用新的 launcher 启动屏幕捕获
    }

    private void stopScreenRecording() {
        Intent serviceIntent = new Intent(getContext(), ScreenRecordingService.class);
        requireContext().stopService(serviceIntent);
    }

    private void collectData(int times) {
        cycleCount = 0; // 重置计数器
        // 显示等待提示
        phrases.setText("准备开始表情...");
        progressBar.setVisibility(View.GONE); // 确保进度条隐藏
        // 启动视频录制
        videoCaptureButton.setEnabled(false);
        collectDataButton.setEnabled(false);
        start.setEnabled(false);

        //executor.execute(this::startVideoRecording);
        // 启动视频录制服务
        Intent serviceIntent = new Intent(getActivity(), VideoRecordingService.class);
        requireActivity().startService(serviceIntent);
        isRecordingVideo = true;
        startAudioRecording();
        cycleHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (cycleCount >= times) {
                    // 停止视频录制
                    //stopVideoRecording();
                    requireContext().stopService(serviceIntent);

                    // 停止音频录制
                    stopAudioRecording();

                    progressBar.setVisibility(View.GONE); // 确保进度条隐藏
                    start.setEnabled(true);
                    videoCaptureButton.setEnabled(true);
                    collectDataButton.setEnabled(true);
                    return; // 完成15个周期后停止

                }
                cycleCount++;

                // 阶段1: 更新提示文本
                phrases.setText("开始做表情！当前第"+cycleCount+"次");
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(0);

                // 阶段2: 启动3秒的进度条
                new Thread(() -> {
                    for (int i = 0; i <= 100; i++) {
                        final int progress = i;
                        getActivity().runOnUiThread(() ->
                                progressBar.setProgress(progress)
                        );
                        try {
                            Thread.sleep(30); // 30ms × 100 = 3秒
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    // 阶段3: 进度条完成后的1秒等待
                    getActivity().runOnUiThread(() -> {
                        phrases.setText("请恢复表情");
                        cycleHandler.postDelayed(() -> {
                            // 开始下一周期
                            cycleHandler.postDelayed(this, 1000); // 下一周期的初始等待1秒
                        }, 825); // 当前周期结束后的等待1秒
                    });
                }).start();
            }
        }, 1000); // 第一个周期的初始等待1秒
    }
    private void startAudioRecording() {
// 获取当前时间的毫秒值
        String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        mAudioRecorder.createDefaultAudio(fileName);
        mAudioRecorder.startRecord(null);
        //start.setText("Stop Recording");
        initCSVFile(FileUtil.getCSVFileAbsolutePath(fileName));
    }

    private void stopAudioRecording() {
        // 停止音频录制
        mAudioRecorder.stopRecord();
        closeCSVWriter();
        start.setText("audio Rec");
        pause.setText("Pause Rec");
        String msg = "AudioRecording success:" + mAudioRecorder.getFilename();
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void stopVideoRecording() {
        // 停止视频录制
        if (recording != null) {
            recording.stop();
            recording = null;
        }
    }



    private void takePhoto() {
        Log.d(TAG, "takePhoto: clicked");
        // Placeholder for take photo logic
        if (imageCapture == null) {
            Log.d(TAG, "imageCapture == null");
            if (isRecordingVideo) {
                // 停止录制

                isRecordingVideo = false;
                imageCaptureButton.setText("take photos");
                // 音频
                stopAudioRecording();
                imageCaptureButton.setEnabled(true);
            } else {
                // 启动录制

                isRecordingVideo = true;
                imageCaptureButton.setText("stop take photos");
                // 音频
                startAudioRecording();
            }
            return;
        }
        Log.d("takePhoto", "ImageCapture is initialized");

        // Create a time-stamped name and MediaStore entry
        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image");
        }

        // Create output options object
        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(
                        requireContext().getContentResolver(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                ).build();

        // Take picture
        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onError(@NonNull ImageCaptureException exc) {
                        Log.e(TAG, "Photo capture failed: " + exc.getMessage(), exc);
                    }

                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        String msg = "Photo capture succeeded: " + output.getSavedUri();
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, msg);
                    }
                }
        );
    }
//    private void compressVideo(String inputFilePath, String outputFilePath) {
//        String command = "-i " + inputFilePath + " -c:v mpeg4  -b:v 500k -vf scale=640:-2 -r 5 " + outputFilePath;
//        Log.d("FFmpegCommand", "Command: " + command);
//        FFmpegKit.executeAsync(command, session -> {
//            if (session.getReturnCode().isValueSuccess()) {
//                File originalFile = new File(inputFilePath);
//                if (originalFile.exists()) {
//                    boolean isDeleted = originalFile.delete();
//                    if (isDeleted) {
//                        Log.d("VideoCompression", "Original video deleted successfully.");
//                    } else {
//                        Log.e("VideoCompression", "Failed to delete the original video.");
//                    }
//                }
//                // 切换到主线程显示成功消息
//                requireActivity().runOnUiThread(() -> {
//                    Toast.makeText(requireContext(), "Video compression succeeded!", Toast.LENGTH_SHORT).show();
//                });
//
//            } else {
//                // 切换到主线程显示失败消息
//                String errorLog = session.getOutput();
//                Log.e("FFmpegError", "Command failed: " + errorLog);
//                requireActivity().runOnUiThread(() ->
//                        Toast.makeText(requireContext(), "Video compression failed: " + errorLog, Toast.LENGTH_SHORT).show()
//                );
//            }
//        });
//    }

    private String getFilePathFromUri(Uri uri) {
        String filePath = null;
        String[] projection = {MediaStore.Video.Media.DATA};

        try (Cursor cursor = requireContext().getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                filePath = cursor.getString(columnIndex);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting file path from Uri: " + e.getMessage());
        }
        return filePath;
    }

    
    @SuppressLint("RestrictedApi")
    private void startCamera() {
        // Placeholder for start camera logic
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Preview setup
                Preview preview = new Preview.Builder().build();

                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                // ImageCapture setup
                //imageCapture = new ImageCapture.Builder().build();

                // ImageAnalysis use case
//                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
//                        .build();
//                imageAnalyzer.setAnalyzer(cameraExecutor, new LuminosityAnalyzer(luma ->
//                        Log.d(TAG, "Average luminosity: " + luma)
//                ));
                // videoCapture 用 recoder 带音频
                Recorder recorder = new Recorder.Builder().setExecutor(cameraExecutor)
                        .setQualitySelector(QualitySelector.from(Quality.LOWEST, FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);


                // Select back camera as a default
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                // Unbind use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                        getViewLifecycleOwner(), // Use fragment's lifecycle
                        cameraSelector,
                        //preview,
                        //imageCapture,
                        videoCapture

                );
                Log.d(TAG, "startCamera: bind");

            } catch (Exception exc) {
                Log.e(TAG, "Use case binding failed", exc);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == REQUEST_CODE_PERMISSIONS) {
//            if (allPermissionsGranted()) {
//                startCamera();
//            } else {
//                Log.e(TAG, "Permissions not granted by the user.");
//            }
//        }
//    }

    //申请录音权限

    private static final int GET_RECODE_AUDIO = 1;

    private static String[] PERMISSION_ALL = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
    };

    /** 申请录音权限*/
    public static void verifyPermissions(Activity activity) {
        boolean permission = (ActivityCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                || (ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                || (ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                || (ActivityCompat.checkSelfPermission(activity, Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED)
                || (ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                ;
        if (permission) {
            ActivityCompat.requestPermissions(activity, PERMISSION_ALL,
                    GET_RECODE_AUDIO);
        }
    }
    // Helper function to get the asset file path
    // Helper function to get the asset file path
    // Helper function to get the asset file path for loading models


    // Helper function to get the resource ID dynamically based on asset name


    private class LuminosityAnalyzer implements ImageAnalysis.Analyzer {

        private final LumaListener listener;

        public LuminosityAnalyzer(LumaListener listener) {
            this.listener = listener;
        }

        private byte[] toByteArray(ByteBuffer buffer) {
            buffer.rewind(); // Rewind the buffer to zero
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data); // Copy the buffer into a byte array
            return data; // Return the byte array
        }

        @Override
        public void analyze(@NonNull ImageProxy image) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] data = toByteArray(buffer);

            // Convert byte array to int list and calculate average luminosity
            List<Integer> pixels = new ArrayList<>();
            for (byte b : data) {
                pixels.add(b & 0xFF);
            }
            double luma = pixels.stream().mapToInt(Integer::intValue).average().orElse(0);

            // Call the listener with the calculated luminosity
            listener.onLumaCalculated(luma);

            image.close();
        }


    }




    public interface LumaListener {
        void onLumaCalculated(double luma);
    }

}