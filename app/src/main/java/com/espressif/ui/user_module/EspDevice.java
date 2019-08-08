package com.espressif.ui.user_module;

import android.os.Parcel;
import android.os.Parcelable;

public class EspDevice implements Parcelable {

    private String deviceId;
    private String deviceName;
    private boolean isOnline;
    private String fwVersion;

    // Constructor
    public EspDevice(String deviceId, String deviceName, boolean status, String fwVersion) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.isOnline = status;
        this.fwVersion = fwVersion;
    }

    public EspDevice() {

    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public String getFwVersion() {
        return fwVersion;
    }

    public void setFwVersion(String fwVersion) {
        this.fwVersion = fwVersion;
    }

    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
        public EspDevice createFromParcel(Parcel in) {
            return new EspDevice(in);
        }

        public EspDevice[] newArray(int size) {
            return new EspDevice[size];
        }
    };

    // Parcelling part
    public EspDevice(Parcel parcel) {
        deviceId = parcel.readString();
        deviceName = parcel.readString();
        isOnline = (Boolean) parcel.readValue(null);
        fwVersion = parcel.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(deviceId);
        dest.writeString(deviceName);
        dest.writeValue(isOnline);
        dest.writeString(fwVersion);
    }

    @Override
    public String toString() {
        return "Device {" +
                "id = '" + deviceId + '\'' +
                ", name ='" + deviceName + '\'' +
                ", status ='" + isOnline + '\'' +
                '}';
    }
}
