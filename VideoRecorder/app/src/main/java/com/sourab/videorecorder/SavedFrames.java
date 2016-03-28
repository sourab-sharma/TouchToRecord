package com.sourab.videorecorder;

/**
 * Created by Sourab Sharma (sourab.sharma@live.in)  on 1/19/2016.
 */
public class SavedFrames {

    private byte[] frameBytesData = null;
    private long timeStamp = 0L;
    private boolean isRotateVideo = false;
    private boolean isFrontCam = false;

    public SavedFrames(byte[] frameBytesData, long timeStamp,boolean isRotateVideo,boolean isFrontCam) {
        setFrameBytesData(frameBytesData);
        setTimeStamp(timeStamp);
        setIsRotateVideo(isRotateVideo);
        setIsFrontCam(isFrontCam);
    }

    public byte[] getFrameBytesData() {
        return frameBytesData;
    }

    public void setFrameBytesData(byte[] frameBytesData) {
        this.frameBytesData = frameBytesData;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(long timeStamp) {
        this.timeStamp = timeStamp;
    }

    public boolean isRotateVideo() {
        return isRotateVideo;
    }

    public void setIsRotateVideo(boolean isRotateVideo) {
        this.isRotateVideo = isRotateVideo;
    }

    public boolean isFrontCam() {
        return isFrontCam;
    }

    public void setIsFrontCam(boolean isFrontCam) {
        this.isFrontCam = isFrontCam;
    }
}
