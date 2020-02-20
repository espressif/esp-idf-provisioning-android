package com.espressif.ui.models;

import android.os.Parcel;
import android.os.Parcelable;

public class DeviceInfo implements Parcelable {

    private String deviceName;
    private String fwVersion;
    private String deviceIp;
    private String connectedWifi;
    private String mac;
    private String serialNumber;
    private boolean isStartToneEnabled;
    private boolean isEndToneEnabled;
    private int language;
    private int volume;
    private boolean isNewFirmware;

    public DeviceInfo() {
    }

    protected DeviceInfo(Parcel in) {

        deviceName = in.readString();
        connectedWifi = in.readString();
        deviceIp = in.readString();
        mac = in.readString();
        serialNumber = in.readString();
        fwVersion = in.readString();
        isStartToneEnabled = in.readByte() != 0;  // isAlexaToneEnabled == true if byte != 0
        isEndToneEnabled = in.readByte() != 0;
        language = in.readInt();
        volume = in.readInt();
        isNewFirmware = in.readByte() != 0;
    }

    public static final Creator<DeviceInfo> CREATOR = new Creator<DeviceInfo>() {
        @Override
        public DeviceInfo createFromParcel(Parcel in) {
            return new DeviceInfo(in);
        }

        @Override
        public DeviceInfo[] newArray(int size) {
            return new DeviceInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(deviceName);
        dest.writeString(connectedWifi);
        dest.writeString(deviceIp);
        dest.writeString(mac);
        dest.writeString(serialNumber);
        dest.writeString(fwVersion);
        dest.writeByte((byte) (isStartToneEnabled ? 1 : 0));     // if isAlexaToneEnabled == true, byte == 1
        dest.writeByte((byte) (isEndToneEnabled ? 1 : 0));
        dest.writeInt(language);
        dest.writeInt(volume);
        dest.writeByte((byte) (isNewFirmware ? 1 : 0));
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getFwVersion() {
        return fwVersion;
    }

    public void setFwVersion(String fwVersion) {
        this.fwVersion = fwVersion;
    }

    public String getDeviceIp() {
        return deviceIp;
    }

    public void setDeviceIp(String deviceIp) {
        this.deviceIp = deviceIp;
    }

    public String getConnectedWifi() {
        return connectedWifi;
    }

    public void setConnectedWifi(String connectedWifi) {
        this.connectedWifi = connectedWifi;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public boolean isStartToneEnabled() {
        return isStartToneEnabled;
    }

    public void setStartToneEnabled(boolean isStartToneEnabled) {
        this.isStartToneEnabled = isStartToneEnabled;
    }

    public boolean isEndToneEnabled() {
        return isEndToneEnabled;
    }

    public void setEndToneEnabled(boolean isEndToneEnabled) {
        this.isEndToneEnabled = isEndToneEnabled;
    }

    public int getLanguage() {
        return language;
    }

    public void setLanguage(int language) {
        this.language = language;
    }

    public int getVolume() {
        return volume;
    }

    public void setVolume(int volume) {
        this.volume = volume;
    }

    public boolean isNewFirmware() {
        return isNewFirmware;
    }

    public void setNewFirmware(boolean isNewFirmware) {
        this.isNewFirmware = isNewFirmware;
    }
}
