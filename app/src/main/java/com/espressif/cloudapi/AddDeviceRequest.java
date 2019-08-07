package com.espressif.cloudapi;

import com.google.gson.annotations.SerializedName;

public class AddDeviceRequest {

    @SerializedName("user_id")
    private String userId;

    @SerializedName("device_id")
    private String deviceId;

    @SerializedName("secret_key")
    private String secretKey;

    @SerializedName("operation")
    private String operation;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }
}
