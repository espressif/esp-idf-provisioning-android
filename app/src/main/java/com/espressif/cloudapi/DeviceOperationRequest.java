package com.espressif.cloudapi;

import com.google.gson.annotations.SerializedName;

public class DeviceOperationRequest {

    @SerializedName("node_id")
    private String nodeId;

    @SerializedName("secret_key")
    private String secretKey;

    @SerializedName("operation")
    private String operation;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
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
