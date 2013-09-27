package com.javacv.recorder;

import static com.googlecode.javacv.cpp.opencv_core.IPL_DEPTH_8U;

import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ShortBuffer;
import java.util.Collections;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.MediaStore.Video;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.googlecode.javacv.FrameRecorder;
import com.googlecode.javacv.cpp.opencv_core.IplImage;






public class FFmpegRecorderActivity extends Activity implements OnClickListener, OnTouchListener {

	private final static String CLASS_LABEL = "RecordActivity";
	private final static String LOG_TAG = CLASS_LABEL;

	private PowerManager.WakeLock mWakeLock;
	private String strAudioPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "rec_audio.mp4";
	private String strVideoPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "rec_video.mp4";
	private String strFinalPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "rec_final.mp4";
	private File fileAudioPath = null;
	private File fileVideoPath = null;
	private File tempFolderPath = null;
	private Uri uriVideoPath = null;
	private boolean rec = false;
	long startTime = 0;
	boolean recording = false, isRecordingStarted = false;

	boolean isFlashOn = false;
	TextView   txtTimer, txtRecordingSize;
	ImageView recorderIcon = null;
	ImageView flashIcon = null, switchCameraIcon = null, resolutionIcon = null;
	private volatile FFmpegFrameRecorder audioRecorder;
	private volatile FFmpegFrameRecorder videoRecorder;
	volatile  long  recordingSize = 0;
	private boolean isPreviewOn = false;
	private int currentResolution = CONSTANTS.RESOLUTION_MEDIUM_VALUE;
	private Camera mCamera;

	private int previewWidth = 320, screenWidth = 320;
	private int previewHeight = 240, screenHeight = 240;
	private int sampleRate = 44100;

	/* audio data getting thread */
	private AudioRecord audioRecord; 
	private AudioRecordRunnable audioRecordRunnable;
	private Thread audioThread;
	volatile boolean runAudioThread = true;

	/* video data getting thread */
	private Camera cameraDevice;
	private CameraView cameraView;
	Parameters cameraParameters = null;
	private IplImage yuvIplImage = null;
	int defaultCameraId = -1, defaultScreenResolution = -1 , cameraSelection = 0;
	/* layout setting */

	private Button btnRecorderControl;

	private Handler mHandler = new Handler();

	private 	Dialog dialog = null;
	RelativeLayout topLayout = null;
	Dialog	selectResolutionDialog = null;
	RelativeLayout previewLayout = null;

	long firstTime = 0;
	long startPauseTime = 0;
	long totalPauseTime = 0;
	long pausedTime = 0;
	long stopPauseTime = 0;
	long totalTime = 0;
	private int frameRate = 30;
	private int recordingTime = 6000000;
	private int recordingMinimumTime = 5000;
	boolean recordFinish = false;
	private  Dialog creatingProgress;

	private volatile long mAudioTimestamp = 0L;
	private final int[] mVideoRecordLock = new int[0];
	private final int[] mAudioRecordLock = new int[0];
	private long mLastAudioTimestamp = 0L;
	private volatile long mAudioTimeRecorded;
	private long frameTime = 0L;
	private SavedFrames lastSavedframe = new SavedFrames(null,0L);
	private long mVideoTimestamp = 0L;
	BroadcastReceiver mReceiver = null;
	private boolean isRecordingSaved = false;
	private boolean isFinalizing = false;
	
	/* this is used to load the 'checkneon' library on application
	 * startup. The library has already been unpacked into
	 * /data/data/com.example.neon/lib/libcheckneon.so at
	 * installation time by the package manager.
	 */
	static {
		System.loadLibrary("checkneon");
	}

	/* A native method that is implemented by the
	 * 'checkneon' native library, which is packaged
	 * with this application.
	 */
	//public native static int  checkNeonFromJNI();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.ffmpeg_recorder);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE); 
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL); 
		mWakeLock.acquire(); 

		DisplayMetrics displaymetrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
		//Find screen dimensions
		screenWidth = displaymetrics.widthPixels;
		screenHeight = displaymetrics.heightPixels;
		
		tempFolderPath = Util.getTempFolderPath();
		if(tempFolderPath != null)
		tempFolderPath.mkdirs();
		
		initLayout();
		initAudioRecorder();
		initVideoRecorder();
	
		startRecording();
	}

	
	@Override
	protected void onResume() {
		super.onResume();
		if (mWakeLock == null) {
			PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
			mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
			mWakeLock.acquire();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if(!isFinalizing)
			finish();
		if (mWakeLock != null) {
			mWakeLock.release();
			mWakeLock = null;
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if(mReceiver != null)
			unregisterReceiver(mReceiver);
		recording = false;
		if (cameraView != null) {
			cameraView.stopPreview();
			if(cameraDevice != null)
			cameraDevice.release();
			cameraDevice = null;
		}
		if (mWakeLock != null) {
			mWakeLock.release();
			mWakeLock = null;
		}
	}
	private void initLayout()
	{
		previewLayout  = (RelativeLayout) (((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.ffmpeg_recorder, null));
		btnRecorderControl = (Button) previewLayout.findViewById(R.id.recorder_control);
		txtTimer = (TextView)  previewLayout.findViewById(R.id.txtTimer);
		txtRecordingSize = (TextView) previewLayout.findViewById(R.id.txtRecordingSize);
		recorderIcon = (ImageView) previewLayout.findViewById(R.id.recorderIcon);
		resolutionIcon = (ImageView) previewLayout.findViewById(R.id.resolutionIcon);
		flashIcon = (ImageView) previewLayout.findViewById(R.id.flashIcon);
		switchCameraIcon = (ImageView) previewLayout.findViewById(R.id.switchCameraIcon);
		btnRecorderControl.setText(getResources().getString(R.string.press));
		btnRecorderControl.setBackgroundResource(R.drawable.btn_shutter_normal);
		btnRecorderControl.setVisibility(View.GONE);
		btnRecorderControl.setOnClickListener(this);
		if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
			flashIcon.setOnClickListener(this);
			flashIcon.setVisibility(View.VISIBLE);
		}
		if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
			switchCameraIcon.setOnClickListener(this);
			switchCameraIcon.setVisibility(View.VISIBLE);
		}
		initCameraLayout();
	}

	private void initCameraLayout() {

		if(topLayout != null && topLayout.getChildCount() > 0)
			topLayout.removeAllViews();
		topLayout = new RelativeLayout(this);
		RelativeLayout.LayoutParams layoutParam = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT);

		setCamera();
		handleSurfaceChanged();

		RelativeLayout.LayoutParams layoutParam1 = new RelativeLayout.LayoutParams(screenWidth,screenHeight);
		layoutParam1.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
		int margin = Util.calculateMargin(previewWidth, screenWidth);
		layoutParam1.setMargins(margin, 0,margin, 0);

		// add the camera preview
		topLayout.addView(cameraView, layoutParam1);
		// add the overlay for buttons and textviews
		topLayout.addView(previewLayout, layoutParam);
		topLayout.setLayoutParams(layoutParam);
		setContentView(topLayout);
		topLayout.setOnTouchListener(this);
	}

	private void setCamera()
	{
		try
		{
			// Find the total number of cameras available
			if(Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO)
			{
				int numberOfCameras = Camera.getNumberOfCameras();
				// Find the ID of the default camera
				CameraInfo cameraInfo = new CameraInfo();
				for (int i = 0; i < numberOfCameras; i++) {
					Camera.getCameraInfo(i, cameraInfo);
					if (cameraInfo.facing == cameraSelection) {
						defaultCameraId = i;
					}
				}
			}
			stopPreview();
			if(mCamera != null)
				mCamera.release();
			if(defaultCameraId >= 0)
				cameraDevice = Camera.open(defaultCameraId);
			else
				cameraDevice = Camera.open();

			cameraView = new CameraView(this, cameraDevice);

		}
		catch(Exception e)
		{
			finish();
		}
	}
	private void initAudioRecorder() {

		// Create a new unique path for video to be created
		strAudioPath = Util.createTempPath(tempFolderPath);
		RecorderParameters recorderParameters = Util.getRecorderParameter(currentResolution);

		sampleRate = recorderParameters.getAudioSamplingRate();
		frameRate = recorderParameters.getVideoFrameRate();
		frameTime = (1000000L / frameRate);
		fileAudioPath= new File(strAudioPath); 

		audioRecorder = new FFmpegFrameRecorder(strAudioPath, previewWidth, previewHeight, 1);
		audioRecorder.setFormat(recorderParameters.getVideoOutputFormat());
		audioRecorder.setSampleRate(recorderParameters.getAudioSamplingRate());
		audioRecorder.setFrameRate(recorderParameters.getVideoFrameRate());
		audioRecorder.setVideoCodec(recorderParameters.getVideoCodec());
		audioRecorder.setVideoQuality(recorderParameters.getVideoQuality()); 
		audioRecorder.setAudioQuality(recorderParameters.getVideoQuality());
		
		audioRecorder.setAudioCodec(recorderParameters.getAudioCodec());


		audioRecorder.setVideoBitrate(recorderParameters.getVideoBitrate());
		audioRecorder.setAudioBitrate(recorderParameters.getAudioBitrate());

		audioRecordRunnable = new AudioRecordRunnable();
		audioThread = new Thread(audioRecordRunnable);
	}


	private void initVideoRecorder() {
		strVideoPath = Util.createTempPath(tempFolderPath);
		RecorderParameters recorderParameters = Util.getRecorderParameter(currentResolution);
		fileVideoPath = new File(strVideoPath); 
		videoRecorder = new FFmpegFrameRecorder(strVideoPath, previewWidth, previewHeight, 1);
		videoRecorder.setFormat(recorderParameters.getVideoOutputFormat());
		videoRecorder.setSampleRate(recorderParameters.getAudioSamplingRate());
		videoRecorder.setFrameRate(recorderParameters.getVideoFrameRate());
		videoRecorder.setVideoCodec(recorderParameters.getVideoCodec());
		videoRecorder.setVideoQuality(recorderParameters.getVideoQuality()); 
		videoRecorder.setAudioQuality(recorderParameters.getVideoQuality());
		videoRecorder.setAudioCodec(recorderParameters.getAudioCodec());
		videoRecorder.setVideoBitrate(1000000);
		videoRecorder.setAudioBitrate(64000);
	}

	public void startRecording() {

		try {
			videoRecorder.start();
			audioRecorder.start();
			audioThread.start();

		} catch (FFmpegFrameRecorder.Exception e) {
			e.printStackTrace();
		}
	}

		public class AsyncStopRecording extends AsyncTask<Void,Void,Void>
		{
			@Override
			protected void onPreExecute() {
				isFinalizing = true;
				recordFinish = true;
				runAudioThread = false;
				creatingProgress= new Dialog(FFmpegRecorderActivity.this);
				creatingProgress.setCanceledOnTouchOutside(false);
				creatingProgress.setTitle(getResources().getString(R.string.finalizing));
				creatingProgress.show();
				recorderIcon.setVisibility(View.GONE);
				txtTimer.setVisibility(View.INVISIBLE);
				btnRecorderControl.setText(getResources().getString(R.string.wait));
				btnRecorderControl.setClickable(false);
				btnRecorderControl.setBackgroundResource(R.drawable.btn_shutter_normal);
				resolutionIcon.setVisibility(View.VISIBLE);
				mHandler.removeCallbacks(mUpdateTimeTask);
				super.onPreExecute();
			}
			
			@Override
			protected Void doInBackground(Void... params) {
				isFinalizing = false;
				if (videoRecorder != null && recording) {
					recording = false;
					releaseResources();
					strFinalPath = Util.createFinalPath();
					Util.combineVideoAndAudio(FFmpegRecorderActivity.this, strVideoPath,strAudioPath,strFinalPath );
				}
				return null;
			}
			
			@Override
			protected void onPostExecute(Void result) {
				creatingProgress.dismiss();
				registerVideo();
				returnToCaller(true);
				videoRecorder = null;
			}
			
		}

		@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {

		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (recording) {
				sendDialog(null);
				return true;
			}
			else
			{
				videoTheEnd(false);
				return false;
			}
		}

		return super.onKeyDown(keyCode, event);
	}

	private void sendDialog(String title)
	{
		dialog = new Dialog(FFmpegRecorderActivity.this);
		if(title != null && title.length() > 0)
		dialog.setTitle(title);
		else
		dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		dialog.setContentView(R.layout.confirmation_dialog);
		dialog.setCanceledOnTouchOutside(true);
		
		((Button) dialog.findViewById(R.id.btnDiscard)).setText(getString(R.string.discard));
		((Button) dialog.findViewById(R.id.btnContinue)).setText(getString(R.string.txt_continue));
		
		((Button) dialog.findViewById(R.id.btnDiscard)).setOnClickListener(this);
		((Button) dialog.findViewById(R.id.btnContinue)).setOnClickListener(this);
		
		dialog.show();
	}

	//---------------------------------------------
		// audio thread, gets and encodes audio data
		//---------------------------------------------
		class AudioRecordRunnable implements Runnable {
			

				// Audio
				int bufferSize;
				short[] audioData;
				int bufferReadResult;
				private final AudioRecord audioRecord;
				public volatile boolean isInitialized;
				private int mCount =0;
			private AudioRecordRunnable()
			{
				bufferSize = AudioRecord.getMinBufferSize(sampleRate, 
						AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
				audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, 
						AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,bufferSize);
				audioData = new short[bufferSize];
			}

			private void record(ShortBuffer shortBuffer)
			{
				try
				{
					synchronized (mAudioRecordLock)
					{
						if (audioRecorder != null)
						{
							this.mCount += shortBuffer.limit();
							audioRecorder.record(new Buffer[] { shortBuffer });
						}
						return;
					}
				}
				catch (FrameRecorder.Exception localException){}
			}

			private void updateTimestamp()
			{
				if (audioRecorder != null)
				{
					int i = Util.getTimeStampInNsFromSampleCounted(this.mCount);
					if (mAudioTimestamp != i)
					{
						mAudioTimestamp = i;
						mAudioTimeRecorded =  System.nanoTime();
					}
				}
			}

			public void run()
			{
				android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

				this.isInitialized = false;
				if(audioRecord != null)
				{
					while (this.audioRecord.getState() == 0)
					{
						try
						{
							Thread.sleep(100L);
						}
						catch (InterruptedException localInterruptedException)
						{
						}
					}
					this.isInitialized = true;
					this.audioRecord.startRecording();
					while (((runAudioThread) || (mVideoTimestamp > mAudioTimestamp)) && (mAudioTimestamp < (1000 * recordingTime)))
					{
						updateTimestamp();
						bufferReadResult = this.audioRecord.read(audioData, 0, audioData.length);
						if ((bufferReadResult > 0) && ((recording && rec) || (mVideoTimestamp > mAudioTimestamp)))
							record(ShortBuffer.wrap(audioData, 0, bufferReadResult));
					}
					this.audioRecord.stop();
					this.audioRecord.release();
				}
			}
		}
		

		//---------------------------------------------
		// camera thread, gets and encodes video data
		//---------------------------------------------
	class CameraView extends SurfaceView implements SurfaceHolder.Callback, PreviewCallback {

		private SurfaceHolder mHolder;


		public CameraView(Context context, Camera camera) {
			super(context);
			mCamera = camera;
			cameraParameters = mCamera.getParameters();
			mHolder = getHolder();
			mHolder.addCallback(CameraView.this);
			mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
			mCamera.setPreviewCallback(CameraView.this);
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			try {
				stopPreview();
				mCamera.setPreviewDisplay(holder);
			} catch (IOException exception) {
				mCamera.release();
				mCamera = null;
			}
		}

		public void surfaceChanged(SurfaceHolder  holder, int format, int width, int height) {
			if (isPreviewOn)
				mCamera.stopPreview();
			handleSurfaceChanged();
			startPreview();  
			mCamera.autoFocus(null);
		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			try {
				mHolder.addCallback(null);
				mCamera.setPreviewCallback(null);
			} catch (RuntimeException e) {
				// The camera has probably just been released, ignore.
			}
		}

		public void startPreview() {
			if (!isPreviewOn && mCamera != null) {
				isPreviewOn = true;
				mCamera.startPreview();
			}
		}

		public void stopPreview() {
			if (isPreviewOn && mCamera != null) {
				isPreviewOn = false;
				mCamera.stopPreview();
			}
		}
		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
			/* get video data */
			long frameTimeStamp = 0L;
			if(mAudioTimestamp == 0L && firstTime > 0L)
				frameTimeStamp = 1000L * (System.currentTimeMillis() -firstTime);
			else if (mLastAudioTimestamp == mAudioTimestamp)
				frameTimeStamp = mAudioTimestamp + frameTime;
			else
			{
				long l2 = (System.nanoTime() - mAudioTimeRecorded) / 1000L;
				frameTimeStamp = l2 + mAudioTimestamp;
				mLastAudioTimestamp = mAudioTimestamp;
			}
			synchronized (mVideoRecordLock) {
				if (recording && rec && lastSavedframe != null && lastSavedframe.getFrameBytesData() != null && yuvIplImage != null) 
				{
					mVideoTimestamp += frameTime;
					if(lastSavedframe.getTimeStamp() > mVideoTimestamp)
					mVideoTimestamp = lastSavedframe.getTimeStamp();
					try {
							yuvIplImage.getByteBuffer().put(lastSavedframe.getFrameBytesData());
							videoRecorder.setTimestamp(lastSavedframe.getTimeStamp());
							videoRecorder.record(yuvIplImage);
						} catch (com.googlecode.javacv.FrameRecorder.Exception e) {
							e.printStackTrace();
						}
				}
				lastSavedframe = new SavedFrames(data,frameTimeStamp);
			}
		}
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {

		if(!recordFinish)
		{
			if(totalTime< recordingTime)
			{
				switch (event.getAction()) 
				{
				case MotionEvent.ACTION_DOWN:
					if(!recording)
					initiateRecording(true);
					else
					{
						stopPauseTime = System.currentTimeMillis();
						totalPauseTime = stopPauseTime - startPauseTime - ((long) (1.0/(double)frameRate)*1000);
						pausedTime += totalPauseTime;
					}
					rec = true;
					setTotalVideoTime();
					btnRecorderControl.setText(getResources().getString(R.string.stop));
					break;
				case MotionEvent.ACTION_MOVE:
					rec = true;
					setTotalVideoTime();
					break;
				case MotionEvent.ACTION_UP:
					rec = false;
					startPauseTime = System.currentTimeMillis();
					break;
				}
			}
			else
			{
				rec = false;
				saveRecording();
			}
		}
		return true;
	}
	public void stopPreview() {
		if (isPreviewOn && mCamera != null) {
			isPreviewOn = false;
			mCamera.stopPreview();

		}
	}

	private void handleSurfaceChanged()
	{
		List<Camera.Size> resolutionList = Util.getResolutionList(mCamera);
		resolutionIcon.setVisibility(View.GONE);	
		if(resolutionList != null && resolutionList.size() > 0)
		{
			Collections.sort(resolutionList, new Util.ResolutionComparator());
			if(resolutionList.size() > 1 && !recording)
			{
				resolutionIcon.setOnClickListener(FFmpegRecorderActivity.this);
				resolutionIcon.setVisibility(View.VISIBLE);
			}
			Camera.Size previewSize =  null;	
			if(defaultScreenResolution == -1)
			{
				int mediumResolution = resolutionList.size()/2;
				if(mediumResolution >= resolutionList.size())
					mediumResolution = resolutionList.size() - 1;
				previewSize = resolutionList.get(mediumResolution);
			}
			else
			{
				if(defaultScreenResolution >= resolutionList.size())
					defaultScreenResolution = resolutionList.size() - 1;
				previewSize = resolutionList.get(defaultScreenResolution);
			}
			if(previewSize != null )
			{
				previewWidth = previewSize.width;
				previewHeight = previewSize.height;
				cameraParameters.setPreviewSize(previewWidth, previewHeight);
				if(videoRecorder != null)
				{
					videoRecorder.setImageWidth(previewWidth);
					videoRecorder.setImageHeight(previewHeight);
				}

			}
		}
		cameraParameters.setPreviewFpsRange(1000, frameRate*1000);
		yuvIplImage = IplImage.create(previewWidth, previewHeight,  IPL_DEPTH_8U, 2);


		if(Build.VERSION.SDK_INT >  Build.VERSION_CODES.FROYO)
		{
			mCamera.setDisplayOrientation(Util.determineDisplayOrientation(FFmpegRecorderActivity.this, defaultCameraId));
			List<String> focusModes = cameraParameters.getSupportedFocusModes();
			if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
			{
				cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
			}
		}
		else
			mCamera.setDisplayOrientation(90);
		mCamera.setParameters(cameraParameters);

	}
	@Override
	public void onClick(View v) {
		List<Camera.Size> resList = Util.getResolutionList(mCamera);
		if(v.getId() == R.id.recorder_control)
		{
			if (isRecordingStarted) {
				rec = false;
				if(totalTime >= recordingMinimumTime)
				saveRecording();
				else
				sendDialog(getResources().getString(R.string.errVideoTime));
			}
			else
				initiateRecording(false);
			}
		else
		if(v.getId() == R.id.flashIcon)
		{
			if(isFlashOn)
			{
				flashIcon.setImageDrawable(getResources().getDrawable(R.drawable.cameraflashoff));
				isFlashOn = false;
				cameraParameters.setFlashMode(Parameters.FLASH_MODE_OFF);
			}
			else
			{
				flashIcon.setImageDrawable(getResources().getDrawable(R.drawable.cameraflash));
				isFlashOn = true;
				cameraParameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
			}
			mCamera.setParameters(cameraParameters);
		}
		else if(v.getId() ==  R.id.resolutionIcon)
		{
			if(!dismissResolutionSelectionDialog())
			{
				selectResolutionDialog = new Dialog(FFmpegRecorderActivity.this, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)
				{
					public boolean onTouchEvent(MotionEvent event) {
						if(event.getAction() == MotionEvent.ACTION_UP){  
							dismissResolutionSelectionDialog();
						}  
						return false;	
					};
				}; 
				selectResolutionDialog.setCanceledOnTouchOutside(true);
				selectResolutionDialog.setContentView(R.layout.dialog_resolution_selector);

				RelativeLayout rootLayout = (RelativeLayout) selectResolutionDialog.findViewById(R.id.rootLayout);
				FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,FrameLayout.LayoutParams.WRAP_CONTENT);
				int[] resolutionIconPos = new int[2];
				resolutionIcon.getLocationOnScreen(resolutionIconPos);
				params.gravity = Gravity.TOP|Gravity.RIGHT;
				params.setMargins(0, resolutionIconPos[1],(resolutionIcon.getWidth()*2) - 10, 0);
				rootLayout.setLayoutParams(params);
				TextView txtHighResolution = (TextView) selectResolutionDialog.findViewById(R.id.txtHighResolution);
				TextView txtMediumResolution = (TextView) selectResolutionDialog.findViewById(R.id.txtMediumResolution);
				TextView txtLowResolution = (TextView) selectResolutionDialog.findViewById(R.id.txtLowResolution);

				RadioButton radioHighResolution = (RadioButton) selectResolutionDialog.findViewById(R.id.radioHighResolution);
				RadioButton radioMediumResolution = (RadioButton) selectResolutionDialog.findViewById(R.id.radioMediumResolution);
				RadioButton radioLowResolution = (RadioButton) selectResolutionDialog.findViewById(R.id.radioLowResolution);

				if(currentResolution == CONSTANTS.RESOLUTION_LOW_VALUE)
				{
					radioHighResolution.setChecked(false);
					radioMediumResolution.setChecked(false);
					radioLowResolution.setChecked(true);
				}
				else if(currentResolution == CONSTANTS.RESOLUTION_MEDIUM_VALUE)
				{
					radioHighResolution.setChecked(false);
					radioMediumResolution.setChecked(true);
					radioLowResolution.setChecked(false);
				}
				else if(currentResolution == CONSTANTS.RESOLUTION_HIGH_VALUE)
				{
					radioHighResolution.setChecked(true);
					radioMediumResolution.setChecked(false);
					radioLowResolution.setChecked(false);
				}
				txtHighResolution.setOnClickListener(this);
				txtMediumResolution.setOnClickListener(this);
				txtLowResolution.setOnClickListener(this);

				radioHighResolution.setOnClickListener(this);
				radioMediumResolution.setOnClickListener(this);
				radioLowResolution.setOnClickListener(this);

				if(resList != null && resList.size() == 2)
					txtMediumResolution.setVisibility(View.GONE);

				Window window = this.getWindow();
				window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
						WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
				window.setFlags(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);

				selectResolutionDialog.show();
			}
		}
		else if(v.getId() == R.id.switchCameraIcon)
		{
			cameraSelection = ((cameraSelection == CameraInfo.CAMERA_FACING_BACK) ? CameraInfo.CAMERA_FACING_FRONT:CameraInfo.CAMERA_FACING_BACK);
			initCameraLayout();

			if(cameraSelection == CameraInfo.CAMERA_FACING_FRONT)
				flashIcon.setVisibility(View.GONE);
			else
			{
				flashIcon.setVisibility(View.VISIBLE);
				if(isFlashOn)
				{
					cameraParameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
					mCamera.setParameters(cameraParameters);
				}
			}
		}
		else if(v.getId() == R.id.btnContinue)
		{
			dialog.dismiss();
		}
		else if(v.getId() == R.id.btnDiscard)
		{
			dialog.dismiss();
			videoTheEnd(false);
		}
		else if(v.getId() == R.id.txtHighResolution || v.getId() == R.id.radioHighResolution)
		{
			if(currentResolution != CONSTANTS.RESOLUTION_HIGH_VALUE)
			{
				setHighResolution(resList.size());
			}
		}
		else if(v.getId() == R.id.txtMediumResolution || v.getId() == R.id.radioMediumResolution)
		{
			if(currentResolution != CONSTANTS.RESOLUTION_MEDIUM_VALUE)
			{
				setMediumResolution(resList.size());
			}
		}
		else if(v.getId() == R.id.txtLowResolution || v.getId() == R.id.radioLowResolution)
		{
			if(currentResolution != CONSTANTS.RESOLUTION_LOW_VALUE)
			{
				setLowResolution();
			}
		}
	}
	private boolean dismissResolutionSelectionDialog()
	{
		if(selectResolutionDialog != null)
		{
			selectResolutionDialog.dismiss();
			selectResolutionDialog = null;
			return true;
		}
		return false;
	}

	private void setHighResolution(int size)
	{
		((RadioButton)(selectResolutionDialog.findViewById(R.id.radioHighResolution))).setChecked(true);
		((RadioButton)(selectResolutionDialog.findViewById(R.id.radioMediumResolution))).setChecked(false);
		((RadioButton)(selectResolutionDialog.findViewById(R.id.radioLowResolution))).setChecked(false);
		defaultScreenResolution = ((size/2) + 1);
		currentResolution = CONSTANTS.RESOLUTION_HIGH_VALUE;
		initCameraLayout();
		dismissResolutionSelectionDialog();
	}

	private void setMediumResolution(int size)
	{
		((RadioButton)(selectResolutionDialog.findViewById(R.id.radioHighResolution))).setChecked(false);
		((RadioButton)(selectResolutionDialog.findViewById(R.id.radioMediumResolution))).setChecked(true);
		((RadioButton)(selectResolutionDialog.findViewById(R.id.radioLowResolution))).setChecked(false);
		defaultScreenResolution = (size/2);
		currentResolution = CONSTANTS.RESOLUTION_MEDIUM_VALUE;
		initCameraLayout();
		dismissResolutionSelectionDialog();
	}

	private void setLowResolution()
	{
		((RadioButton)(selectResolutionDialog.findViewById(R.id.radioHighResolution))).setChecked(false);
		((RadioButton)(selectResolutionDialog.findViewById(R.id.radioMediumResolution))).setChecked(false);
		((RadioButton)(selectResolutionDialog.findViewById(R.id.radioLowResolution))).setChecked(true);
		defaultScreenResolution = 0;
		currentResolution = CONSTANTS.RESOLUTION_LOW_VALUE;
		initCameraLayout();
		dismissResolutionSelectionDialog();
	}


	public void videoTheEnd(boolean isSuccess)
	{
		releaseResources();
		
		if(fileAudioPath != null && fileAudioPath.exists() && !isSuccess)
			fileAudioPath.delete();
		if(fileVideoPath != null && fileVideoPath.exists() && !isSuccess)
			fileVideoPath.delete();
		returnToCaller(isSuccess);
	}

		private Runnable mUpdateTimeTask = new Runnable() {
		public void run() {
			if(rec)
			setTotalVideoTime();
			mHandler.postDelayed(this, 500);
		}
	};
	private void returnToCaller(boolean valid)
	{
		try
		{
			setActivityResult(valid);
			finish();
		} catch (Throwable e)
		{
		}
	}
	
	private void setActivityResult(boolean valid)
	{
		Intent resultIntent = new Intent();
		if(tempFolderPath != null)
		resultIntent.putExtra(CONSTANTS.KEY_DELETE_FOLDER_FROM_SDCARD, tempFolderPath.getAbsolutePath());
		int resultCode;
		if (valid)
		{
			resultCode = RESULT_OK;
			resultIntent.setData(uriVideoPath);
		} else
			resultCode = RESULT_CANCELED;
		
		setResult(resultCode, resultIntent);
	}


	private void registerVideo()
	{
		Uri videoTable = Uri.parse(CONSTANTS.VIDEO_CONTENT_URI);
		Util.videoContentValues.put(Video.Media.SIZE, new File(strFinalPath).length());
		try
		{
			uriVideoPath = getContentResolver().insert(videoTable, Util.videoContentValues);
		} catch (Throwable e)
		{
			// We failed to insert into the database. This can happen if
			// the SD card is unmounted.
			uriVideoPath = null;
			strFinalPath = null;
			e.printStackTrace();
		} finally
		{}
		Util.videoContentValues = null;
	}


	private void saveRecording()
	{
		if(isRecordingStarted)
		{
			runAudioThread = false;
			// This will make the executor accept no new threads
		    // and finish all existing threads in the queue
			if(!isRecordingSaved)
			{
		   //  pool.shutdown();
		    // Wait until all threads are finish
		   // pool.awaitTermination(firstTime, null);
				isRecordingSaved = true;
				new AsyncStopRecording().execute();
			}
		}
		else
		{
			videoTheEnd(false);
		}
	}

	
	private synchronized void setTotalVideoTime()
	{
		totalTime = System.currentTimeMillis() - firstTime - pausedTime - ((long) (1.0/(double)frameRate)*1000);
		if(totalTime > 0)
			txtTimer.setText(Util.getRecordingTimeFromMillis(totalTime));
	}
	
private void releaseResources()
	{
		isRecordingSaved = true;
		try {
			if(videoRecorder != null)
			{
			videoRecorder.stop();
			videoRecorder.release();
			}
			if(audioRecorder != null)
			{
			audioRecorder.stop();
			audioRecorder.release();
			}
		} catch (com.googlecode.javacv.FrameRecorder.Exception e) {
			e.printStackTrace();
		}
		
		yuvIplImage = null;
		videoRecorder = null;
		audioRecorder = null;
		lastSavedframe = null;
	}
	
	private void initiateRecording(boolean isActionDown)
	{
		isRecordingStarted = true;
		firstTime = System.currentTimeMillis();
	
		recording = true;
		totalPauseTime = 0;
		pausedTime = 0;
		
		txtTimer.setVisibility(View.VISIBLE);
		// Handler to show recoding duration after recording starts
		mHandler.removeCallbacks(mUpdateTimeTask);
		mHandler.postDelayed(mUpdateTimeTask, 100);	
		btnRecorderControl.setVisibility(View.VISIBLE);
		btnRecorderControl.setText(getResources().getString(R.string.stop));
	}
}