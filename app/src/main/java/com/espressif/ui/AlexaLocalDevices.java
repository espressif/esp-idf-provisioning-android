package com.espressif.ui;

public class AlexaLocalDevices {
    private String modelno;
    private String manufacturer;
    private String hostAddr;
    private String status;
    private String softwareVersion;
    private String friendlyName;

    AlexaLocalDevices(String hostAddr){
        this.hostAddr = hostAddr;
        this.friendlyName = null;
    }
    public String getHostAddress() {
        return hostAddr;
    }
    public String getFriendlyName() {
        return friendlyName;
    }
    public String getStatus(){
        return status;
    }
    public String getSoftwareVersion(){
        return softwareVersion;
    }


    public void setModelno (String modelno){
        this.modelno = modelno;
    }

    public void setStatus(String status){
        this.status = status;
    }

    public void setManufacturer (String manufacturer){
        this.manufacturer = manufacturer;
    }

    public void setSoftwareVersion(String softwareVersion){
        this.softwareVersion = softwareVersion;
    }

    public void setFriendlyName(String friendlyName){
        this.friendlyName = friendlyName;
    }
}
