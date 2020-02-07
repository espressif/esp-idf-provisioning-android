package com.espressif.ui.models;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

public class EspNode implements Parcelable {

    private String nodeId;
    private String configVersion;
    private String nodeName;
    private String fwVersion;
    private String nodeType;
    private boolean isOnline;
    private ArrayList<EspDevice> devices;
    private ArrayList<Param> attributes;

    public EspNode(String id) {
        nodeId = id;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(String configVersion) {
        this.configVersion = configVersion;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getFwVersion() {
        return fwVersion;
    }

    public void setFwVersion(String fwVersion) {
        this.fwVersion = fwVersion;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public ArrayList<EspDevice> getDevices() {
        return devices;
    }

    public void setDevices(ArrayList<EspDevice> devices) {
        this.devices = devices;
    }

    public ArrayList<Param> getAttributes() {
        return attributes;
    }

    public void setAttributes(ArrayList<Param> attributes) {
        this.attributes = attributes;
    }

    protected EspNode(Parcel in) {

        nodeId = in.readString();
        configVersion = in.readString();
        nodeName = in.readString();
        fwVersion = in.readString();
        nodeType = in.readString();
        isOnline = in.readByte() != 0;
        devices = in.createTypedArrayList(EspDevice.CREATOR);
        attributes = in.createTypedArrayList(Param.CREATOR);
    }

    public static final Creator<EspNode> CREATOR = new Creator<EspNode>() {
        @Override
        public EspNode createFromParcel(Parcel in) {
            return new EspNode(in);
        }

        @Override
        public EspNode[] newArray(int size) {
            return new EspNode[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

        dest.writeString(nodeId);
        dest.writeString(configVersion);
        dest.writeString(nodeName);
        dest.writeString(fwVersion);
        dest.writeString(nodeType);
        dest.writeByte((byte) (isOnline ? 1 : 0));
        dest.writeTypedList(devices);
        dest.writeTypedList(attributes);
    }
}
