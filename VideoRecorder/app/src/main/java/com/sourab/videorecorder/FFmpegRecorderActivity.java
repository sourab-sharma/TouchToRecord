package com.sourab.videorecorder;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.provider.MediaStore.Video;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.sourab.videorecorder.interfaces.Interfaces;
import com.sourab.videorecorder.util.CustomUtil;

import org.bytedeco.javacv.FrameRecorder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ShortBuffer;
import java.util.Collections;
import java.util.List;

/**
 * Created by Sourab Sharma (sourab.sharma@live.in)  on 1/19/2016.
 */
public class FFmpegRecorderActivity extends Activity implements OnClickListener, OnTouchListener, Interfaces.AddBitmapOverlayListener {

    private final static String CLASS_LABEL = "RecordActivity";
    private PowerManager.WakeLock mWakeLock;
    private String strVideoPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "rec_video.mp4";
    private File fileVideoPath = null;
    private Uri uriVideoPath = null;
    private AudioRecordRunnable audioRecordRunnable;
    private Thread audioThread;
    private Camera cameraDevice;
    private CameraView cameraView;
    private Parameters cameraParameters = null;
    private volatile FFmpegFrameRecorder videoRecorder;
    private RecorderThread recorderThread;
    private Dialog creatingProgress;

    private Button flashIcon, cancelBtn, nextBtn, switchCameraIcon;
    private RelativeLayout topLayout = null;
    private SavedFrames lastSavedframe = new SavedFrames(null, 0L, false, false);

    private boolean recording = false;
    private boolean isRecordingStarted = false;
    private boolean isFlashOn = false;
    private boolean isRotateVideo = false;
    private boolean isFrontCam = false;
    private boolean isPreviewOn = false;
    private boolean nextEnabled = false;
    private boolean recordFinish = false;
    volatile boolean runAudioThread = true;
    private boolean isRecordingSaved = false;
    private boolean isFinalizing = false;

    private int currentResolution = CONSTANTS.RESOLUTION_MEDIUM_VALUE;
    private int previewWidth = 480;
    private int screenWidth = 480;
    private int previewHeight = 480;
    private int sampleRate = 44100;
    private int defaultCameraId = -1;
    private int defaultScreenResolution = -1;
    private int cameraSelection = 0;
    private int frameRate = 30;
    private int totalRecordingTime = 6000;
    private int minRecordingTime = 3000;

    private long firstTime = 0;
    private long startPauseTime = 0;
    private long totalPauseTime = 0;
    private long pausedTime = 0;
    private long stopPauseTime = 0;
    private long totalTime = 0;

    private volatile long mAudioTimestamp = 0L;
    private long mLastAudioTimestamp = 0L;
    private long frameTime = 0L;
    private long mVideoTimestamp = 0L;
    private volatile long mAudioTimeRecorded;

    private ProgressView progressView;
    private TextView stateTextView;
    private String imagePath = null;
    private RecorderState currentRecorderState = RecorderState.PRESS;

    private byte[] firstData = null;
    private byte[] bufferByte;

    private DeviceOrientationEventListener orientationListener;
    // The degrees of the device rotated clockwise from its natural orientation.
    private int deviceOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private Handler mHandler;


    private void initHandler() {
        mHandler = new Handler() {
            @Override
            public void dispatchMessage(Message msg) {
                switch (msg.what) {
                    case 2:
                        int recorderStateMsg = 0;
                        if (currentRecorderState == RecorderState.RECORDING) {
                            recorderStateMsg = R.string.recorder_state_recording;
                        } else if (currentRecorderState == RecorderState.MINIMUM_RECORDING_REACHED) {
                            recorderStateMsg = R.string.recorder_state_min_video_crossed;
                        } else if (currentRecorderState == RecorderState.MINIMUM_RECORDED) {
                            recorderStateMsg = R.string.recorder_state_min_recorded;
                        } else if (currentRecorderState == RecorderState.PRESS) {
                            recorderStateMsg = R.string.recorder_state_press_to_record;
                        } else if (currentRecorderState == RecorderState.SUCCESS) {
                            recorderStateMsg = R.string.recorder_state_complete;
                        }
                        stateTextView.setText(getResources().getText(recorderStateMsg));
                        break;
                    case 3:
                        if (!isRecordingStarted)
                            initiateRecording();
                        else {
                            stopPauseTime = System.currentTimeMillis();
                            totalPauseTime = stopPauseTime - startPauseTime - ((long) (1.0 / (double) frameRate) * 1000);
                            pausedTime += totalPauseTime;
                        }
                        recording = true;
                        progressView.setCurrentState(ProgressView.State.START);
                        currentRecorderState = RecorderState.RECORDING;
                        mHandler.sendEmptyMessage(2);
                        break;
                    case 4:
                        progressView.setCurrentState(ProgressView.State.PAUSE);
                        progressView.putProgressList((int) totalTime);
                        recording = false;
                        startPauseTime = System.currentTimeMillis();
                        if (totalTime >= totalRecordingTime) {
                            currentRecorderState = RecorderState.SUCCESS;
                            mHandler.sendEmptyMessage(2);
                        } else if (totalTime >= minRecordingTime) {
                            currentRecorderState = RecorderState.MINIMUM_RECORDED;
                            mHandler.sendEmptyMessage(2);
                        } else {
                            currentRecorderState = RecorderState.PRESS;
                            mHandler.sendEmptyMessage(2);
                        }
                        break;
                    case 5:
                        currentRecorderState = RecorderState.SUCCESS;
                        mHandler.sendEmptyMessage(2);
                        break;
                    default:
                        break;
                }
            }
        };
    }

	/*static {
        System.loadLibrary("checkneon");
	}*/

    //public native static int  checkNeonFromJNI();
    private boolean initSuccess = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_recorder);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
        mWakeLock.acquire();

        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        //Find screen dimensions
        screenWidth = displaymetrics.widthPixels;

        orientationListener = new DeviceOrientationEventListener(FFmpegRecorderActivity.this);

        initHandler();

        initLayout();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!initSuccess)
            return false;
        return super.dispatchTouchEvent(ev);
    }


    @Override
    protected void onResume() {
        super.onResume();
        mHandler.sendEmptyMessage(2);

        if (orientationListener != null)
            orientationListener.enable();

        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
            mWakeLock.acquire();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isFinalizing)
            finish();
        if (orientationListener != null)
            orientationListener.disable();
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRecordingStarted = false;
        runAudioThread = false;

        releaseResources();

        if (cameraView != null) {
            cameraView.stopPreview();
            if (cameraDevice != null) {
                cameraDevice.setPreviewCallback(null);
                cameraDevice.release();
            }
            cameraDevice = null;
        }
        firstData = null;
        cameraDevice = null;
        cameraView = null;
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    private void initLayout() {
        stateTextView = (TextView) findViewById(R.id.recorder_surface_state);
        progressView = (ProgressView) findViewById(R.id.recorder_progress);
        progressView.setTotalTime(totalRecordingTime);
        cancelBtn = (Button) findViewById(R.id.recorder_cancel);
        cancelBtn.setOnClickListener(this);
        nextBtn = (Button) findViewById(R.id.recorder_next);
        nextBtn.setOnClickListener(this);
        flashIcon = (Button) findViewById(R.id.recorder_flashlight);
        switchCameraIcon = (Button) findViewById(R.id.recorder_frontcamera);
        flashIcon.setOnClickListener(this);

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            switchCameraIcon.setVisibility(View.VISIBLE);
        }
        initCameraLayout();
    }

    private void initCameraLayout() {
        new AsyncTask<String, Integer, Boolean>() {

            @Override
            protected Boolean doInBackground(String... params) {
                boolean result = setCamera();
                if (!initSuccess) {
                    initVideoRecorder();
                    startRecording();
                    initSuccess = true;
                }
                return result;
            }

            @Override
            protected void onPostExecute(Boolean result) {
                if (!result || cameraDevice == null) {
                    finish();
                    return;
                }

                topLayout = (RelativeLayout) findViewById(R.id.recorder_surface_parent);
                if (topLayout != null && topLayout.getChildCount() > 0)
                    topLayout.removeAllViews();

                cameraView = new CameraView(FFmpegRecorderActivity.this, cameraDevice);

                handleSurfaceChanged();
                if (recorderThread == null) {
                    recorderThread = new RecorderThread(videoRecorder, previewWidth, previewHeight);
                    recorderThread.start();
                }
                RelativeLayout.LayoutParams layoutParam1 = new RelativeLayout.LayoutParams(screenWidth, (int) (screenWidth * (previewWidth / (previewHeight * 1f))));
                layoutParam1.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
                RelativeLayout.LayoutParams layoutParam2 = new RelativeLayout.LayoutParams(screenWidth,screenWidth);
                layoutParam2.topMargin = screenWidth;

                View view = new View(FFmpegRecorderActivity.this);
                view.setFocusable(false);
                view.setBackgroundColor(Color.BLACK);
                view.setFocusableInTouchMode(false);

                topLayout.addView(cameraView, layoutParam1);
              //  topLayout.addView(view, layoutParam2);

                topLayout.setOnTouchListener(FFmpegRecorderActivity.this);

                switchCameraIcon.setOnClickListener(FFmpegRecorderActivity.this);
                if (cameraSelection == CameraInfo.CAMERA_FACING_FRONT) {
                    flashIcon.setVisibility(View.GONE);
                    isFrontCam = true;
                } else {
                    flashIcon.setVisibility(View.VISIBLE);
                    isFrontCam = false;
                }
            }

        }.execute("start");
    }

    private boolean setCamera() {
        try {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO) {
                int numberOfCameras = Camera.getNumberOfCameras();
                CameraInfo cameraInfo = new CameraInfo();
                for (int i = 0; i < numberOfCameras; i++) {
                    Camera.getCameraInfo(i, cameraInfo);
                    if (cameraInfo.facing == cameraSelection) {
                        defaultCameraId = i;
                    }
                }
            }
            stopPreview();
            if (cameraDevice != null)
                cameraDevice.release();

            if (defaultCameraId >= 0)
                cameraDevice = Camera.open(defaultCameraId);
            else
                cameraDevice = Camera.open();

        } catch (Exception e) {
            return false;
        }
        return true;
    }


    private void initVideoRecorder() {
        strVideoPath = Util.createFinalPath(this);

        RecorderParameters recorderParameters = Util.getRecorderParameter(currentResolution);
        sampleRate = recorderParameters.getAudioSamplingRate();
        frameRate = recorderParameters.getVideoFrameRate();
        frameTime = (1000000L / frameRate);

        fileVideoPath = new File(strVideoPath);
        videoRecorder = new FFmpegFrameRecorder(strVideoPath, CONSTANTS.OUTPUT_WIDTH, CONSTANTS.OUTPUT_HEIGHT, recorderParameters.getAudioChannel());
        videoRecorder.setFormat(recorderParameters.getVideoOutputFormat());
        videoRecorder.setSampleRate(recorderParameters.getAudioSamplingRate());
        videoRecorder.setFrameRate(recorderParameters.getVideoFrameRate());
        videoRecorder.setVideoCodec(recorderParameters.getVideoCodec());
        videoRecorder.setVideoQuality(recorderParameters.getVideoQuality());
        videoRecorder.setAudioQuality(recorderParameters.getVideoQuality());
        videoRecorder.setAudioCodec(recorderParameters.getAudioCodec());
        videoRecorder.setVideoBitrate(recorderParameters.getVideoBitrate());
        videoRecorder.setAudioBitrate(recorderParameters.getAudioBitrate());
        //videoRecorder.setVideoOption(recorderParameters.getAudioBitrate());
        audioRecordRunnable = new AudioRecordRunnable();
        audioThread = new Thread(audioRecordRunnable);
    }

    public void startRecording() {
        try {
            if (videoRecorder != null)
                videoRecorder.start();
            else finish();
            if (audioThread != null)
                audioThread.start();
            else finish();
        } catch (FFmpegFrameRecorder.Exception e) {
            e.printStackTrace();
        }
    }

    public class AsyncStopRecording extends AsyncTask<Void, Integer, Void> {

        private ProgressBar bar;
        private TextView progress;

        @Override
        protected void onPreExecute() {
            isFinalizing = true;
            recordFinish = true;
            runAudioThread = false;

            creatingProgress = new Dialog(FFmpegRecorderActivity.this, R.style.Dialog_loading_noDim);
            Window dialogWindow = creatingProgress.getWindow();
            WindowManager.LayoutParams lp = dialogWindow.getAttributes();
            lp.width = (int) (getResources().getDisplayMetrics().density * 240);
            lp.height = (int) (getResources().getDisplayMetrics().density * 80);
            lp.gravity = Gravity.CENTER;
            dialogWindow.setAttributes(lp);
            creatingProgress.setCanceledOnTouchOutside(false);
            creatingProgress.setContentView(R.layout.activity_recorder_progress);

            progress = (TextView) creatingProgress.findViewById(R.id.recorder_progress_progresstext);
            bar = (ProgressBar) creatingProgress.findViewById(R.id.recorder_progress_progressbar);
            creatingProgress.show();

            super.onPreExecute();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progress.setText(values[0] + "%");
            bar.setProgress(values[0]);
        }

        private void getFirstCapture(byte[] data) {
            String captureBitmapPath = Util.createImagePath(FFmpegRecorderActivity.this);
            YuvImage localYuvImage = new YuvImage(data, 17, previewWidth, previewHeight, null);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            FileOutputStream outStream = null;

            try {
                File file = new File(captureBitmapPath);
                if (!file.exists())
                    file.createNewFile();
                localYuvImage.compressToJpeg(new Rect(0, 0, previewWidth, previewHeight), 100, bos);
                Bitmap localBitmap1 = BitmapFactory.decodeByteArray(bos.toByteArray(),
                        0, bos.toByteArray().length);

                bos.close();

                Matrix localMatrix = new Matrix();
                if (cameraSelection == 0)
                    localMatrix.setRotate(90.0F);
                else
                    localMatrix.setRotate(270.0F);

                Bitmap localBitmap2 = Bitmap.createBitmap(localBitmap1, 0, 0,
                        localBitmap1.getHeight(),
                        localBitmap1.getHeight(),
                        localMatrix, true);

                ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
                localBitmap2.compress(Bitmap.CompressFormat.JPEG, 100, bos2);

                outStream = new FileOutputStream(captureBitmapPath);
                outStream.write(bos2.toByteArray());
                outStream.close();

                localBitmap1.recycle();
                localBitmap2.recycle();

                isFirstFrame = false;
                imagePath = captureBitmapPath;
            } catch (FileNotFoundException e) {
                isFirstFrame = true;
                e.printStackTrace();
            } catch (IOException e) {
                isFirstFrame = true;
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {
            if (firstData != null)
                getFirstCapture(firstData);

            recorderThread.stopRecord();

            isFinalizing = false;
            if (videoRecorder != null && isRecordingStarted) {
                isRecordingStarted = false;
                releaseResources();
            }
            String finalOutputPath = null;
            if (CONSTANTS.DO_YOU_WANT_WATER_MARK_ON_VIDEO) {
                publishProgress(50);
                File file = Util.createWatermarkFilePath(FFmpegRecorderActivity.this);;
                try {
                    if(file != null && !file.exists()) {
                        Bitmap watermark = BitmapFactory.decodeResource(getResources(), R.drawable.replace_it_with_your_watermark);
                        FileOutputStream outStream = new FileOutputStream(file);
                        watermark.compress(Bitmap.CompressFormat.PNG, 100, outStream);
                        outStream.flush();
                        outStream.close();
                        publishProgress(55);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }


                finalOutputPath =  Util.createFinalPath(FFmpegRecorderActivity.this);
                if (!new File(finalOutputPath).exists()) {
                    try {
                        new File(finalOutputPath).createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                publishProgress(60);
                CustomUtil.addBitmapOverlayOnVideo(FFmpegRecorderActivity.this, strVideoPath, file.getAbsolutePath(), finalOutputPath);
                strVideoPath = finalOutputPath;
            }
            publishProgress(100);
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            if (!isFinishing()) {
                creatingProgress.dismiss();
            }
            registerVideo();
            returnToCaller(true);
            videoRecorder = null;
        }

    }

    private void showCancellDialog() {
        Util.showDialog(FFmpegRecorderActivity.this, getResources().getString(R.string.alert), getResources().getString(R.string.discard_video_msg), 2, new Handler() {
            @Override
            public void dispatchMessage(Message msg) {
                if (msg.what == 1)
                    videoTheEnd(false);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (isRecordingStarted)
            showCancellDialog();
        else
            videoTheEnd(false);
    }

    class AudioRecordRunnable implements Runnable {

        int bufferSize;
        short[] audioData;
        int bufferReadResult;
        private final AudioRecord audioRecord;
        public volatile boolean isInitialized;
        private int mCount = 0;

        private AudioRecordRunnable() {
            bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            audioData = new short[bufferSize];
        }

        private void record(ShortBuffer shortBuffer) {
            try {
                if (videoRecorder != null) {
                    this.mCount += shortBuffer.limit();
                    videoRecorder.recordSamples(new Buffer[]{shortBuffer});
                }
            } catch (FrameRecorder.Exception localException) {

            }
            return;
        }

        private void updateTimestamp() {
            if (videoRecorder != null) {
                int i = Util.getTimeStampInNsFromSampleCounted(this.mCount);
                if (mAudioTimestamp != i) {
                    mAudioTimestamp = i;
                    mAudioTimeRecorded = System.nanoTime();
                }
            }
        }

        public void run() {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            this.isInitialized = false;
            if (audioRecord != null) {
                while (this.audioRecord.getState() == 0) {
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException localInterruptedException) {
                    }
                }
                this.isInitialized = true;
                this.audioRecord.startRecording();
                while (((runAudioThread) || (mVideoTimestamp > mAudioTimestamp)) && (mAudioTimestamp < (1000 * totalRecordingTime))) {
                    updateTimestamp();
                    bufferReadResult = this.audioRecord.read(audioData, 0, audioData.length);
                    if ((bufferReadResult > 0) && ((isRecordingStarted && recording) || (mVideoTimestamp > mAudioTimestamp)))
                        record(ShortBuffer.wrap(audioData, 0, bufferReadResult));
                }
                this.audioRecord.stop();
                this.audioRecord.release();
            }
        }
    }


    private boolean isFirstFrame = true;

    class CameraView extends SurfaceView implements SurfaceHolder.Callback, PreviewCallback {

        private SurfaceHolder mHolder;


        public CameraView(Context context, Camera camera) {
            super(context);
            cameraDevice = camera;
            cameraParameters = cameraDevice.getParameters();
            mHolder = getHolder();
            mHolder.addCallback(CameraView.this);
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
            cameraDevice.setPreviewCallbackWithBuffer(CameraView.this);
        }


        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                stopPreview();
                cameraDevice.setPreviewDisplay(holder);
            } catch (IOException exception) {
                cameraDevice.release();
                cameraDevice = null;
            }
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (isPreviewOn)
                cameraDevice.stopPreview();
            handleSurfaceChanged();
            startPreview();
            cameraDevice.autoFocus(null);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            try {
                mHolder.addCallback(null);
                cameraDevice.setPreviewCallback(null);

            } catch (RuntimeException e) {
            }
        }

        public void startPreview() {
            if (!isPreviewOn && cameraDevice != null) {
                isPreviewOn = true;
                cameraDevice.startPreview();
            }
        }

        public void stopPreview() {
            if (isPreviewOn && cameraDevice != null) {
                isPreviewOn = false;
                cameraDevice.stopPreview();
            }
        }

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            long frameTimeStamp = 0L;
            if (mAudioTimestamp == 0L && firstTime > 0L) {
                frameTimeStamp = 1000L * (System.currentTimeMillis() - firstTime);
            } else if (mLastAudioTimestamp == mAudioTimestamp) {
                frameTimeStamp = mAudioTimestamp + frameTime;
            } else {
                long l2 = (System.nanoTime() - mAudioTimeRecorded) / 1000L;
                frameTimeStamp = l2 + mAudioTimestamp;
                mLastAudioTimestamp = mAudioTimestamp;
            }

            if (isRecordingStarted && recording) {
                if (lastSavedframe != null
                        && lastSavedframe.getFrameBytesData() != null) {
                    if (isFirstFrame) {
                        isFirstFrame = false;
                        firstData = data;
                    }
                    totalTime = System.currentTimeMillis() - firstTime - pausedTime - ((long) (1.0 / (double) frameRate) * 1000);
                    if (!nextEnabled && totalTime >= minRecordingTime) {
                        nextEnabled = true;
                        nextBtn.setEnabled(true);
                    }
                    if (nextEnabled && totalTime >= totalRecordingTime) {
                        mHandler.sendEmptyMessage(5);
                    }
                    if (currentRecorderState == RecorderState.RECORDING && totalTime >= minRecordingTime) {
                        currentRecorderState = RecorderState.MINIMUM_RECORDING_REACHED;
                        mHandler.sendEmptyMessage(2);
                    }

                    mVideoTimestamp += frameTime;
                    if (lastSavedframe.getTimeStamp() > mVideoTimestamp) {
                        mVideoTimestamp = lastSavedframe.getTimeStamp();
                    }
                    recorderThread.putByteData(lastSavedframe);
                }
            }
            lastSavedframe = new SavedFrames(data, frameTimeStamp, isRotateVideo, isFrontCam);
            cameraDevice.addCallbackBuffer(bufferByte);
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        if (!recordFinish) {
            if (totalTime < totalRecordingTime) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (deviceOrientation == 0) {
                            isRotateVideo = true;
                        } else {
                            isRotateVideo = false;
                        }
                        mHandler.removeMessages(3);
                        mHandler.removeMessages(4);
                        mHandler.sendEmptyMessageDelayed(3, 300);
                        break;
                    case MotionEvent.ACTION_UP:
                        mHandler.removeMessages(3);
                        if (recording) {
                            recording = false;
                            mHandler.removeMessages(4);
                            mHandler.sendEmptyMessage(4);
                        }

                        break;
                }
            } else {

                recording = false;
                saveRecording();
            }
        }
        return true;
    }

    public void stopPreview() {
        if (isPreviewOn && cameraDevice != null) {
            isPreviewOn = false;
            cameraDevice.stopPreview();
        }
    }

    private void handleSurfaceChanged() {
        if (cameraDevice == null) {
            finish();
            return;
        }
        List<Camera.Size> resolutionList = Util.getResolutionList(cameraDevice);
        if (resolutionList != null && resolutionList.size() > 0) {
            Collections.sort(resolutionList, new Util.ResolutionComparator());
            Camera.Size previewSize = null;
            if (defaultScreenResolution == -1) {
                boolean hasSize = false;
                for (int i = 0; i < resolutionList.size(); i++) {
                    Size size = resolutionList.get(i);
                    if (size != null && size.width == 640 && size.height == 480) {
                        previewSize = size;
                        hasSize = true;
                        break;
                    }
                }
                if (!hasSize) {
                    int mediumResolution = resolutionList.size() / 2;
                    if (mediumResolution >= resolutionList.size())
                        mediumResolution = resolutionList.size() - 1;
                    previewSize = resolutionList.get(mediumResolution);
                }
            } else {
                if (defaultScreenResolution >= resolutionList.size())
                    defaultScreenResolution = resolutionList.size() - 1;
                previewSize = resolutionList.get(defaultScreenResolution);
            }
            if (previewSize != null) {
                previewWidth = previewSize.width;
                previewHeight = previewSize.height;
                cameraParameters.setPreviewSize(previewWidth, previewHeight);
                if (videoRecorder != null) {
                    videoRecorder.setImageWidth(previewWidth);
                    videoRecorder.setImageHeight(previewHeight);
                }
            }
        }

        bufferByte = new byte[previewWidth * previewHeight * 3 / 2];

        cameraDevice.addCallbackBuffer(bufferByte);

        cameraParameters.setPreviewFrameRate(frameRate);


        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO) {
            cameraDevice.setDisplayOrientation(Util.determineDisplayOrientation(FFmpegRecorderActivity.this, defaultCameraId));
            List<String> focusModes = cameraParameters.getSupportedFocusModes();
            if (focusModes != null) {
                Log.i("video", Build.MODEL);
                if (((Build.MODEL.startsWith("GT-I950"))
                        || (Build.MODEL.endsWith("SCH-I959"))
                        || (Build.MODEL.endsWith("MEIZU MX3"))) && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {

                    cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
                    cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
                }
            }
        } else
            cameraDevice.setDisplayOrientation(90);
        cameraDevice.setParameters(cameraParameters);
    }

    @Override
    public void onClick(View v) {
        // If else is used here if you need it as library project, switch case will not work
        if (v.getId() == R.id.recorder_next) {
            if (isRecordingStarted) {
                recording = false;
                saveRecording();
            } else
                initiateRecording();
        } else if (v.getId() == R.id.recorder_flashlight) {
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
                return;
            }
            if (isFlashOn) {
                isFlashOn = false;
                flashIcon.setSelected(false);
                cameraParameters.setFlashMode(Parameters.FLASH_MODE_OFF);
            } else {
                isFlashOn = true;
                flashIcon.setSelected(true);
                cameraParameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
            }
            cameraDevice.setParameters(cameraParameters);
        } else if (v.getId() == R.id.recorder_frontcamera) {
            cameraSelection = ((cameraSelection == CameraInfo.CAMERA_FACING_BACK) ? CameraInfo.CAMERA_FACING_FRONT : CameraInfo.CAMERA_FACING_BACK);
            isFrontCam = ((cameraSelection == CameraInfo.CAMERA_FACING_BACK) ? false : true);

            initCameraLayout();

            if (cameraSelection == CameraInfo.CAMERA_FACING_FRONT)
                flashIcon.setVisibility(View.GONE);
            else {
                flashIcon.setVisibility(View.VISIBLE);
                if (isFlashOn) {
                    cameraParameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
                    cameraDevice.setParameters(cameraParameters);
                }
            }
        } else if (v.getId() == R.id.recorder_cancel) {
            if (isRecordingStarted)
                showCancellDialog();
            else
                videoTheEnd(false);
        }
    }


    public void videoTheEnd(boolean isSuccess) {
        releaseResources();
        if (fileVideoPath != null && fileVideoPath.exists() && !isSuccess)
            fileVideoPath.delete();

        returnToCaller(isSuccess);
    }

    private void returnToCaller(boolean valid) {
        try {
            setActivityResult(valid);
            if (valid) {
                Intent intent = new Intent(this, FFmpegPreviewActivity.class);
                intent.putExtra("path", strVideoPath);
                intent.putExtra("imagePath", imagePath);
                startActivity(intent);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            finish();
        }
    }

    private void setActivityResult(boolean valid) {
        Intent resultIntent = new Intent();
        int resultCode;
        if (valid) {
            resultCode = RESULT_OK;
            resultIntent.setData(uriVideoPath);
        } else
            resultCode = RESULT_CANCELED;

        setResult(resultCode, resultIntent);
    }

    private void registerVideo() {
        Uri videoTable = Uri.parse(CONSTANTS.VIDEO_CONTENT_URI);
        Util.videoContentValues.put(Video.Media.SIZE, new File(strVideoPath).length());
        try {
            uriVideoPath = getContentResolver().insert(videoTable, Util.videoContentValues);
        } catch (Throwable e) {
            uriVideoPath = null;
            strVideoPath = null;
            e.printStackTrace();
        } finally {
        }
        Util.videoContentValues = null;
    }

    private void saveRecording() {
        if (isRecordingStarted) {
            runAudioThread = false;
            if (!isRecordingSaved) {
                isRecordingSaved = true;
                new AsyncStopRecording().execute();
            }
        } else {
            videoTheEnd(false);
        }
    }

    private void releaseResources() {
        if (recorderThread != null) {
            recorderThread.finish();
        }
        isRecordingSaved = true;
        try {
            if (videoRecorder != null) {
                videoRecorder.stop();
                videoRecorder.release();
            }
        } catch (FrameRecorder.Exception e) {
            e.printStackTrace();
        }
        videoRecorder = null;
        lastSavedframe = null;
        if (progressView != null)
            progressView.setCurrentState(ProgressView.State.PAUSE);
    }

    private void initiateRecording() {
        firstTime = System.currentTimeMillis();
        isRecordingStarted = true;
        totalPauseTime = 0;
        pausedTime = 0;
    }

    public enum RecorderState {
        PRESS(1), RECORDING(2), MINIMUM_RECORDING_REACHED(3), MINIMUM_RECORDED(4), SUCCESS(5);

        static RecorderState mapIntToValue(final int stateInt) {
            for (RecorderState value : RecorderState.values()) {
                if (stateInt == value.getIntValue()) {
                    return value;
                }
            }
            return PRESS;
        }

        private int mIntValue;

        RecorderState(int intValue) {
            mIntValue = intValue;
        }

        int getIntValue() {
            return mIntValue;
        }
    }

    private class DeviceOrientationEventListener
            extends OrientationEventListener {
        public DeviceOrientationEventListener(Context context) {
            super(context);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            deviceOrientation = Util.roundOrientation(orientation, deviceOrientation);
            if (deviceOrientation == 0) {
                isRotateVideo = true;
            } else {
                isRotateVideo = false;
            }
        }
    }

    @Override
    public void OnBitmapOverlayAdded(int position) {

    }
}