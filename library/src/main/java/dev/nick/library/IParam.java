package dev.nick.library;

import android.os.Parcel;
import android.os.Parcelable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Created by Tornaco on 2017/7/24.
 * Licensed with Apache.
 */
@Builder
@Getter
@AllArgsConstructor
@ToString
public class IParam implements Parcelable {

    // Audio
    private int audioSource;
    private int audioBitrate;
    private int audioSampleRate;

    // Video
    private int orientation;
    private int frameRate;
    private int iFrameInterval;
    private String resolution;

    private boolean shutterSound;
    private boolean stopOnScreenOff;
    private boolean stopOnShake;
    private boolean useMediaProjection;
    private boolean showNotification;
    private boolean showTouch;

    private String path;


    protected IParam(Parcel in) {
        audioSource = in.readInt();
        audioBitrate = in.readInt();
        audioSampleRate = in.readInt();
        orientation = in.readInt();
        frameRate = in.readInt();
        iFrameInterval = in.readInt();
        resolution = in.readString();
        shutterSound = in.readByte() != 0;
        stopOnScreenOff = in.readByte() != 0;
        stopOnShake = in.readByte() != 0;
        useMediaProjection = in.readByte() != 0;
        showNotification = in.readByte() != 0;
        showTouch = in.readByte() != 0;
        path = in.readString();
    }

    public static final Creator<IParam> CREATOR = new Creator<IParam>() {
        @Override
        public IParam createFromParcel(Parcel in) {
            return new IParam(in);
        }

        @Override
        public IParam[] newArray(int size) {
            return new IParam[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeInt(audioSource);
        parcel.writeInt(audioBitrate);
        parcel.writeInt(audioSampleRate);
        parcel.writeInt(orientation);
        parcel.writeInt(frameRate);
        parcel.writeInt(iFrameInterval);
        parcel.writeString(resolution);
        parcel.writeByte((byte) (shutterSound ? 1 : 0));
        parcel.writeByte((byte) (stopOnScreenOff ? 1 : 0));
        parcel.writeByte((byte) (stopOnShake ? 1 : 0));
        parcel.writeByte((byte) (useMediaProjection ? 1 : 0));
        parcel.writeByte((byte) (showNotification ? 1 : 0));
        parcel.writeByte((byte) (showTouch ? 1 : 0));
        parcel.writeString(path);
    }
}
