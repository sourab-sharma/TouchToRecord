package com.sourab.videorecorder;

import android.os.Build;

import org.bytedeco.javacpp.avcodec;

/**
 * Created by Sourab Sharma (sourab.sharma@live.in)  on 1/19/2016.
 */
public class RecorderParameters {

    private static boolean AAC_SUPPORTED = Build.VERSION.SDK_INT >= 10;
    private int videoCodec = avcodec.AV_CODEC_ID_H264;
    private int videoFrameRate = 30;
    private int videoQuality = 5;
    private int audioCodec = AAC_SUPPORTED ? avcodec.AV_CODEC_ID_AAC : avcodec.AV_CODEC_ID_AMR_NB;
    private int audioChannel = 1;
    private int audioBitrate = 96000;//192000;//AAC_SUPPORTED ? 96000 : 12200;
    private int videoBitrate = 1000000;
    private int audioSamplingRate = AAC_SUPPORTED ? 44100 : 8000;
    private String videoOutputFormat = AAC_SUPPORTED ? "mp4" : "3gp";

    public String getVideoOutputFormat() {
        return videoOutputFormat;
    }

    public int getAudioSamplingRate() {
        return audioSamplingRate;
    }

    public int getVideoCodec() {
        return videoCodec;
    }

    public int getVideoFrameRate() {
        return videoFrameRate;
    }

    public int getVideoQuality() {
        return videoQuality;
    }

    public void setVideoQuality(int videoQuality) {
        this.videoQuality = videoQuality;
    }

    public int getAudioCodec() {
        return audioCodec;
    }

    public int getAudioBitrate() {
        return audioBitrate;
    }

    public void setAudioBitrate(int audioBitrate) {
        this.audioBitrate = audioBitrate;
    }

    public int getVideoBitrate() {
        return videoBitrate;
    }

    public int getAudioChannel() {
        return audioChannel;
    }

}
