package com.sourab.touch.to.record.demo;



import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.Toast;
import android.widget.VideoView;

import com.javacv.recorder.FFmpegRecorderActivity;

public class MainActivity extends Activity implements OnClickListener
{
	Button btnStart;
	public static boolean		IS_CUSTOM_CAMERA_ENABLED									= true;
	public static final int		CUSTOM_ACTION_VIDEO_CAPTURE	                                = 101;
	VideoView	videoView 																	= null;
	public static final int		MAX_VIDEO_DURATION_ALLOWED									= 5 * 60;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		checkIsCustomCameraSupported();
		initialize();
	}

	private void checkIsCustomCameraSupported()
	{
		int isNeon = com.javacv.recorder.Util.checkNeonFeature();
		if (isNeon == 0)
		{
			IS_CUSTOM_CAMERA_ENABLED = false;
			Toast.makeText(MainActivity.this, "Custom Camera is not supported", Toast.LENGTH_LONG).show();
			finish();
		}
	}
	
	private void initialize()
	{
		btnStart = (Button) findViewById(R.id.btnStart);
		btnStart.setOnClickListener(this);
		videoView = (VideoView) findViewById(R.id.videoView);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btnStart:
			if(videoView != null && videoView.isPlaying())
			{
				finish();
			}
			else
			{
			Intent intent = new Intent(MainActivity.this, FFmpegRecorderActivity.class);
			startActivityForResult(intent, CUSTOM_ACTION_VIDEO_CAPTURE);
			}
			break;
		default:
			break;
		}
		
	}
	
	private void playRecordedVideo(Uri videoUri, boolean playVideoInLoop)
	{
		RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
		
		videoView.setLayoutParams(layoutParams);
		
		videoView.setVisibility(View.VISIBLE);
		videoView.setVideoURI(videoUri);
		if(playVideoInLoop)
		{
			MediaController mediaController = new MediaController(MainActivity.this);
			mediaController.setAnchorView(videoView);
			videoView.setMediaController(mediaController);
			videoView.setOnPreparedListener (new OnPreparedListener() {                    
			    @Override
			    public void onPrepared(MediaPlayer mp) {
			        mp.setLooping(true);
			    }
			});
		}
		else
		{
			videoView.setMediaController(null);
		}
		videoView.start();
		btnStart.setText(getString(R.string.txt_finish));
	}
	
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		getRecordedVideo(resultCode,data);
	}
	
	
	void getRecordedVideo(int resultCode,Intent data )
	{
		if (resultCode == RESULT_CANCELED)
		{
			finishAsError();
			return;
		}
		boolean ifValidFile = false;
		Uri uri = null;
		if (resultCode == RESULT_OK && null != data)
		{
			uri = data.getData();
			if (uri != null)
			{
				String path = null;
				path = getPath(uri);
				if (null != path)
				{
					File f = new File(path);
					if (null != f && f.exists())
					{
						
							/**
							 * Following TRY block will be helpful only if you want to Limit the duration of recorded video by user.
							 * Otherwise comment it .
							 */

								try
								{
									Cursor cursor = MediaStore.Video.query(getContentResolver(), uri, new String[]
											{ MediaStore.Video.VideoColumns.DURATION });
									if (cursor != null)
									{
										if (cursor.getCount() > 0)
										{
											cursor.moveToFirst();

											String duration = cursor.getString(cursor.getColumnIndex("duration"));
											if (duration != null && !duration.equalsIgnoreCase(""))
											{
												Long durationInt = Long.parseLong(duration);
												durationInt = durationInt / 1000;
												if (durationInt > (MAX_VIDEO_DURATION_ALLOWED + 5))
												{
													finishAsError();
													return;
												}

											}

										}
										cursor.close();
									}
								}
								catch (Exception e)
								{
								}
							
					
					
						ifValidFile = true;
					}
				}
				
				if (!ifValidFile)
				{
					finishAsError();
					return;
				}
				else
				{
					playRecordedVideo(uri, true);
				}
			}
		}
		
	}
	
	private void finishAsError()
	{
		Toast.makeText(this, getString(R.string.unable_to_get_file), Toast.LENGTH_SHORT).show();
	}
	
	public String getPath(Uri uri)
	{
		String filePath = null;
		try
		{
			String[] projection =
				{ MediaStore.Video.Media.DATA };
			Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
			if (cursor.moveToFirst())
			{
				filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA));
			}
		}
		catch (Throwable e)
		{
		}
		return filePath;
	}
}
