package com.espressif.ui.models;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

public class EspDevice implements Parcelable {

    private String nodeId;
    private String deviceName;
    private String deviceType;
    private String primaryParamName;
    private ArrayList<Param> params;

    public EspDevice(String id) {
        nodeId = id;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getPrimaryParamName() {
        return primaryParamName;
    }

    public void setPrimaryParamName(String primaryParamName) {
        this.primaryParamName = primaryParamName;
    }

    public ArrayList<Param> getParams() {
        return params;
    }

    public void setParams(ArrayList<Param> params) {
        this.params = params;
    }

    protected EspDevice(Parcel in) {

        nodeId = in.readString();
        deviceName = in.readString();
        deviceType = in.readString();
        primaryParamName = in.readString();
        params = in.createTypedArrayList(Param.CREATOR);
    }

    public static final Creator<EspDevice> CREATOR = new Creator<EspDevice>() {
        @Override
        public EspDevice createFromParcel(Parcel in) {
            return new EspDevice(in);
        }

        @Override
        public EspDevice[] newArray(int size) {
            return new EspDevice[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

        dest.writeString(nodeId);
        dest.writeString(deviceName);
        dest.writeString(deviceType);
        dest.writeString(primaryParamName);
        dest.writeTypedList(params);
    }

    @Override
    public String toString() {
        return "EspDevice {" +
                "node id = '" + nodeId + '\'' +
                "name = '" + deviceName + '\'' +
                ", type ='" + deviceType + '\'' +
                '}';
    }
}
