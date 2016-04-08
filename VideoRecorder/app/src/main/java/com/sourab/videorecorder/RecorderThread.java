package com.sourab.videorecorder;

import android.util.Log;

import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacv.FFmpegFrameFilter;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameFilter;
import org.bytedeco.javacv.FrameRecorder;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Sourab Sharma (sourab.sharma@live.in)  on 1/19/2016.
 */
public class RecorderThread extends Thread {

    private FFmpegFrameRecorder mVideoRecorder;
    private AtomicBoolean mIsStop = new AtomicBoolean(false);
    private AtomicBoolean mIsFinish = new AtomicBoolean(false);

    private Frame yuvFrame;
    private ArrayList<SavedFrames> savedFrameList = null;

    private FFmpegFrameFilter filterRotateVideoFrontCam = null;
    private FFmpegFrameFilter filterRotateVideoBackCam = null;
    private FFmpegFrameFilter filterCropVideoBackCam = null;
    private FFmpegFrameFilter filterCropVideoFrontCam = null;

    private int previewWidth = 0, previewHeight = 0;


    public RecorderThread(FFmpegFrameRecorder videoRecorder, int previewWidth, int previewHeight) {
        try {
            this.previewWidth = previewWidth;
            this.previewHeight = previewHeight;
            savedFrameList = new ArrayList<SavedFrames>();
            this.yuvFrame = new Frame(previewWidth, previewHeight, Frame.DEPTH_UBYTE, 2);
            this.mVideoRecorder = videoRecorder;
            setFilters();
        } catch (Exception e) {
        }
    }

    public void putByteData(SavedFrames lastSavedframe) {
        if(savedFrameList != null)
        savedFrameList.add(lastSavedframe);
    }

    @Override
    public void run() {
        try {
            while (!mIsFinish.get()) {
                if (processFramesFromList()) {


                } else {
                    if (mIsStop.get()) {
                        break;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } finally {
            release();
        }
    }

    private boolean processFramesFromList() {
        boolean isListPending = false;
        if (savedFrameList != null && savedFrameList.size() > 0) {
            SavedFrames savedFrame = savedFrameList.get(0);
            savedFrameList.remove(0);
            mVideoRecorder.setTimestamp(savedFrame.getTimeStamp());
            processBytesUsingFrame(savedFrame);
            if (savedFrameList.size() > 0) {
                isListPending = true;
            }
        }
        return isListPending;
    }


    public void stopRecord() {
        mIsStop.set(true);
        try {
            this.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void finish() {
        mIsFinish.set(true);
        try {
            this.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void release() {
        mVideoRecorder = null;
        yuvFrame = null;
        savedFrameList = null;
        try {
            if (filterRotateVideoFrontCam != null) {
                filterRotateVideoFrontCam.stop();
                filterRotateVideoFrontCam.release();
                filterRotateVideoFrontCam = null;
            }
            if (filterRotateVideoBackCam != null) {
                filterRotateVideoBackCam.stop();
                filterRotateVideoBackCam.release();
                filterRotateVideoBackCam = null;
            }
            if (filterCropVideoBackCam != null) {
                filterCropVideoBackCam.stop();
                filterCropVideoBackCam.release();
                filterCropVideoBackCam = null;
            }
            if (filterCropVideoFrontCam != null) {
                filterCropVideoFrontCam.stop();
                filterCropVideoFrontCam.release();
                filterCropVideoFrontCam = null;
            }
        }
        catch (FrameFilter.Exception e)
        {
            Log.e("RecorderThread release" , e.getMessage());
        }
    }


    private void processBytesUsingFrame(SavedFrames frame) {
        ((ByteBuffer) yuvFrame.image[0].position(0)).put(frame.getFrameBytesData());
        Frame pulledFrame = null;

        if (frame.isRotateVideo()) {
            if (frame.isFrontCam()) {
                try {
                    filterRotateVideoFrontCam.push(yuvFrame);
                    while ((pulledFrame = filterRotateVideoFrontCam.pull()) != null) {
                        mVideoRecorder.record(pulledFrame);
                    }
                } catch (FrameFilter.Exception e) {
                    e.printStackTrace();
                } catch (FrameRecorder.Exception e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    filterRotateVideoBackCam.push(yuvFrame);
                    while ((pulledFrame = filterRotateVideoBackCam.pull()) != null) {
                        mVideoRecorder.record(pulledFrame);
                    }
                } catch (FrameFilter.Exception e) {
                    e.printStackTrace();
                } catch (FrameRecorder.Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            if (frame.isFrontCam()) {
                try {
                    filterCropVideoFrontCam.push(yuvFrame);
                    while ((pulledFrame = filterCropVideoFrontCam.pull()) != null) {
                        mVideoRecorder.record(pulledFrame);
                    }
                } catch (FrameFilter.Exception e) {
                    e.printStackTrace();
                } catch (FrameRecorder.Exception e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    filterCropVideoBackCam.push(yuvFrame);
                    while ((pulledFrame = filterCropVideoBackCam.pull()) != null) {
                        mVideoRecorder.record(pulledFrame);
                    }
                } catch (FrameFilter.Exception e) {
                    e.printStackTrace();
                } catch (FrameRecorder.Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void setFilters() {
        String cropVideo = "crop=w=" + CONSTANTS.OUTPUT_WIDTH + ":h=" + CONSTANTS.OUTPUT_HEIGHT + ":x=" + 0 + ":y=" + 0;
        String rotateVideoFrontCam = "transpose=cclock,hflip," + cropVideo;
        String rotateVideoBackCam = "transpose=clock," + cropVideo;
        String cropVideoFrontCam = "hflip," + cropVideo;

        filterRotateVideoFrontCam = new FFmpegFrameFilter(rotateVideoFrontCam, previewWidth, previewHeight);
        filterRotateVideoFrontCam.setPixelFormat(avutil.AV_PIX_FMT_NV21);

        filterRotateVideoBackCam = new FFmpegFrameFilter(rotateVideoBackCam, previewWidth, previewHeight);
        filterRotateVideoBackCam.setPixelFormat(avutil.AV_PIX_FMT_NV21);

        filterCropVideoBackCam = new FFmpegFrameFilter(cropVideo, previewWidth, previewHeight);
        filterCropVideoBackCam.setPixelFormat(avutil.AV_PIX_FMT_NV21);

        filterCropVideoFrontCam = new FFmpegFrameFilter(cropVideoFrontCam, previewWidth, previewHeight);
        filterCropVideoFrontCam.setPixelFormat(avutil.AV_PIX_FMT_NV21);

        try {
            filterRotateVideoFrontCam.start();
            filterRotateVideoBackCam.start();
            filterCropVideoBackCam.start();
            filterCropVideoFrontCam.start();
        } catch (FrameFilter.Exception e) {
            Log.e("RecordThread setFilter", e.getMessage());
        }
    }
}
