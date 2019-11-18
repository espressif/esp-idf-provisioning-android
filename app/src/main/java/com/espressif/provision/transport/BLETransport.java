package com.espressif.provision.transport;

import android.bluetooth.BluetoothDevice;

import java.util.ArrayList;
import java.util.UUID;

public abstract class BLETransport implements Transport {

    public ArrayList<String> deviceCapabilities;

    public abstract void connect(BluetoothDevice bluetoothDevice, UUID primaryServiceUuid);

    public abstract void disconnect();

    /***
     * Listener which will receive events relating to BLE device scanning
     */
    public interface BLETransportListener {

        /**
         * Peripheral device configured.
         * This tells the caller that the connected BLE device is now configured
         * and can be provisioned
         *
         * @param device
         */
        void onPeripheralConfigured(BluetoothDevice device);

        /**
         * Peripheral device could not be configured.
         * This tells the called that the connected device cannot be configured for provisioning
         *
         * @param device
         */
        void onPeripheralNotConfigured(BluetoothDevice device);

        /**
         * Peripheral device disconnected
         *
         * @param e
         */
        void onPeripheralDisconnected(Exception e);

        /**
         * Failed to scan for BLE bluetoothDevices
         *
         * @param e
         */
        void onFailure(Exception e);
    }
}
