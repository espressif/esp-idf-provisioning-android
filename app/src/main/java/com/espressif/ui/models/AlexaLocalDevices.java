package com.espressif.ui.models;

import java.io.Serializable;

public class AlexaLocalDevices implements Serializable {
    private String modelno;
    private String hostAddr;
    private String status;
    private String softwareVersion;
    private String friendlyName;
    private String version;
    private String deviceName;

    public AlexaLocalDevices(String hostAddr) {
        this.hostAddr = hostAddr;
        this.friendlyName = null;
    }

    public String getHostAddress() {
        return hostAddr;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public String getStatus() {
        return status;
    }

    public String getSoftwareVersion() {
        return softwareVersion;
    }

    public String getModelno() {
        return modelno;
    }


    public void setModelno(String modelno) {
        this.modelno = modelno;
    }

    public void setStatus(String status) {
        this.status = status;
    }


    public void setSoftwareVersion(String softwareVersion) {
        this.softwareVersion = softwareVersion;
    }

    public void setFriendlyName(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }
}
