package com.sourab.videorecorder;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

import java.util.Iterator;
import java.util.LinkedList;
/**
 * Created by Sourab Sharma (sourab.sharma@live.in)  on 1/19/2016.
 */
public class ProgressView extends View
{
	public ProgressView(Context context) {
		super(context);
		init(context);
	}

	public ProgressView(Context paramContext, AttributeSet paramAttributeSet) {
		super(paramContext, paramAttributeSet);
		init(paramContext);
	}

	public ProgressView(Context paramContext, AttributeSet paramAttributeSet,
						int paramInt) {
		super(paramContext, paramAttributeSet, paramInt);
		init(paramContext);
	}

	private Paint progressPaint, firstPaint, threePaint,breakPaint;
	private float firstWidth = 4f, threeWidth = 1f;
	private LinkedList<Integer> linkedList = new LinkedList<Integer>();
	private float perPixel = 0l;
	private float countRecorderTime = 6000;

	public void setTotalTime(float time){
		countRecorderTime = time;
	}

	private void init(Context paramContext) {

		progressPaint = new Paint();
		firstPaint = new Paint();
		threePaint = new Paint();
		breakPaint = new Paint();

		setBackgroundColor(Color.parseColor("#19000000"));

		progressPaint.setStyle(Paint.Style.FILL);
		progressPaint.setColor(Color.parseColor("#19e3cf"));

		firstPaint.setStyle(Paint.Style.FILL);
		firstPaint.setColor(Color.parseColor("#ffcc42"));

		threePaint.setStyle(Paint.Style.FILL);
		threePaint.setColor(Color.parseColor("#12a899"));

		breakPaint.setStyle(Paint.Style.FILL);
		breakPaint.setColor(Color.parseColor("#000000"));

		DisplayMetrics dm = new DisplayMetrics();
		((Activity)paramContext).getWindowManager().getDefaultDisplay().getMetrics(dm);
		perPixel = dm.widthPixels/countRecorderTime;
		perSecProgress = perPixel;
	}


	public enum State {
		START(0x1),PAUSE(0x2);

		static State mapIntToValue(final int stateInt) {
			for (State value : State.values()) {
				if (stateInt == value.getIntValue()) {
					return value;
				}
			}
			return PAUSE;
		}

		private int mIntValue;

		State(int intValue) {
			mIntValue = intValue;
		}

		int getIntValue() {
			return mIntValue;
		}
	}


	private volatile State currentState = State.PAUSE;
	private boolean isVisible = true;
	private float countWidth = 0;
	private float perProgress = 0;
	private float perSecProgress = 0;
	private long initTime;
	private long drawFlashTime = 0;

	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		long curTime = System.currentTimeMillis();
		countWidth = 0;
		if(!linkedList.isEmpty()){
			float frontTime = 0;
			Iterator<Integer> iterator = linkedList.iterator();
			while(iterator.hasNext()){
				int time = iterator.next();
				float left = countWidth;
				countWidth += (time-frontTime)*perPixel;
				canvas.drawRect(left, 0,countWidth,getMeasuredHeight(),progressPaint);
				canvas.drawRect(countWidth, 0,countWidth + threeWidth,getMeasuredHeight(),breakPaint);
				countWidth += threeWidth;
				frontTime = time;
			}

			if(linkedList.getLast() <= 3000)
				canvas.drawRect(perPixel*3000, 0,perPixel*3000+threeWidth,getMeasuredHeight(),threePaint);
		}else
			canvas.drawRect(perPixel*3000, 0,perPixel*3000+threeWidth,getMeasuredHeight(),threePaint);

		if(currentState == State.START){
			perProgress += perSecProgress*(curTime - initTime );
			if(countWidth + perProgress <= getMeasuredWidth())
				canvas.drawRect(countWidth, 0,countWidth + perProgress,getMeasuredHeight(),progressPaint);
			else
				canvas.drawRect(countWidth, 0,getMeasuredWidth(),getMeasuredHeight(),progressPaint);
		}
		if(drawFlashTime==0 || curTime - drawFlashTime >= 500){
			isVisible = !isVisible;
			drawFlashTime = System.currentTimeMillis();
		}
		if(isVisible){
			if(currentState == State.START)
				canvas.drawRect(countWidth + perProgress, 0,countWidth + firstWidth + perProgress,getMeasuredHeight(),firstPaint);
			else
				canvas.drawRect(countWidth, 0,countWidth + firstWidth,getMeasuredHeight(),firstPaint);
		}
		initTime = System.currentTimeMillis();
		invalidate();
	}

	public void setCurrentState(State state){
		currentState = state;
		if(state == State.PAUSE)
			perProgress = perSecProgress;
	}

	public void putProgressList(int time) {
		linkedList.add(time);
	}
}