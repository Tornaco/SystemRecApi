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

    private int audioSource;
    private int orientation;
    private int frameRate;
    private String resolution;
    private boolean shutterSound;
    private boolean stopOnScreenOff;
    private boolean stopOnShake;

    private String path;


    protected IParam(Parcel in) {
        audioSource = in.readInt();
        orientation = in.readInt();
        frameRate = in.readInt();
        resolution = in.readString();
        shutterSound = in.readByte() != 0;
        stopOnScreenOff = in.readByte() != 0;
        stopOnShake = in.readByte() != 0;
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
        parcel.writeInt(orientation);
        parcel.writeInt(frameRate);
        parcel.writeString(resolution);
        parcel.writeByte((byte) (shutterSound ? 1 : 0));
        parcel.writeByte((byte) (stopOnScreenOff ? 1 : 0));
        parcel.writeByte((byte) (stopOnShake ? 1 : 0));
        parcel.writeString(path);
    }
}
