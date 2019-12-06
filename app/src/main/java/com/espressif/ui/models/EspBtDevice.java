package com.espressif.ui.models;

import android.bluetooth.BluetoothDevice;

public class EspBtDevice {

    private BluetoothDevice bluetoothDevice;
    private String serviceUuid;
    private boolean isLatestProvMethod;

    public EspBtDevice(BluetoothDevice bluetoothDevice, String serviceUuid, boolean isNewProvMethod) {
        this.bluetoothDevice = bluetoothDevice;
        this.serviceUuid = serviceUuid;
        this.isLatestProvMethod = isNewProvMethod;
    }

    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    public void setBluetoothDevice(BluetoothDevice bluetoothDevice) {
        this.bluetoothDevice = bluetoothDevice;
    }

    public String getServiceUuid() {
        return serviceUuid;
    }

    public void setServiceUuid(String serviceUuid) {
        this.serviceUuid = serviceUuid;
    }

    public boolean isLatestProvMethod() {
        return isLatestProvMethod;
    }

    public void setLatestProvMethod(boolean latestProvMethod) {
        isLatestProvMethod = latestProvMethod;
    }
}
