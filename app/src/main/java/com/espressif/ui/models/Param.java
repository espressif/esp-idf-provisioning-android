package com.espressif.ui.models;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

public class Param implements Parcelable {

    private String name;
    private String dataType;
    private String uiType;
    private ArrayList<String> properties;
    private int minBounds;
    private int maxBounds;
    private int sliderValue;
    private boolean switchStatus;
    private String labelValue;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getUiType() {
        return uiType;
    }

    public void setUiType(String uiType) {
        this.uiType = uiType;
    }

    public ArrayList<String> getProperties() {
        return properties;
    }

    public void setProperties(ArrayList<String> properties) {
        this.properties = properties;
    }

    public int getMinBounds() {
        return minBounds;
    }

    public void setMinBounds(int minBounds) {
        this.minBounds = minBounds;
    }

    public int getMaxBounds() {
        return maxBounds;
    }

    public void setMaxBounds(int maxBounds) {
        this.maxBounds = maxBounds;
    }

    public int getSliderValue() {
        return sliderValue;
    }

    public void setSliderValue(int sliderValue) {
        this.sliderValue = sliderValue;
    }

    public boolean getSwitchStatus() {
        return switchStatus;
    }

    public void setSwitchStatus(boolean switchStatus) {
        this.switchStatus = switchStatus;
    }

    public String getLabelValue() {
        return labelValue;
    }

    public void setLabelValue(String labelValue) {
        this.labelValue = labelValue;
    }

    public Param() {
    }

    protected Param(Parcel in) {
        name = in.readString();
        dataType = in.readString();
        uiType = in.readString();
        properties = in.createStringArrayList();
        minBounds = in.readInt();
        maxBounds = in.readInt();
        sliderValue = in.readInt();
        switchStatus = in.readByte() != 0;
        labelValue = in.readString();
    }

    public static final Creator<Param> CREATOR = new Creator<Param>() {
        @Override
        public Param createFromParcel(Parcel in) {
            return new Param(in);
        }

        @Override
        public Param[] newArray(int size) {
            return new Param[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(dataType);
        dest.writeString(uiType);
        dest.writeStringList(properties);
        dest.writeInt(minBounds);
        dest.writeInt(maxBounds);
        dest.writeInt(sliderValue);
        dest.writeByte((byte) (switchStatus ? 1 : 0));
        dest.writeString(labelValue);
    }

    @Override
    public String toString() {
        return "Param {" +
                "name = '" + name + '\'' +
                ", dataType ='" + dataType + '\'' +
                ", uiType ='" + uiType + '\'' +
                '}';
    }
}
