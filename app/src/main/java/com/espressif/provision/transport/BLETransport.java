// Copyright 2018 Espressif Systems (Shanghai) PTE LTD
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.espressif.provision.transport;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Build;
import android.util.Log;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Bluetooth implementation of the Transport protocol.
 */
public class BLETransport implements Transport {

    private static final String TAG = "Espressif::" + BLETransport.class.getSimpleName();

    public static final String SERVICE_UUID_KEY = "serviceUUID";
    public static final String SESSION_UUID_KEY = "sessionUUID";
    public static final String CONFIG_UUID_KEY = "configUUID";
    public static final String DEVICE_NAME_PREFIX_KEY = "deviceNamePrefix";

    private final UUID sessionCharacteristicUuid;
    private Activity context;
    private UUID serviceUuid;
    private BluetoothDevice currentDevice;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattService service;
    private BluetoothGattCharacteristic sessionCharacteristic;
    private HashMap<String, String> configUUIDMap;
    private BLETransportListener transportListener;
    private ResponseListener currentResponseListener;
    private Semaphore transportToken;
    private ExecutorService dispatcherThreadPool;

    /***
     * Create BLETransport implementation
     * @param context
     * @param serviceUuid string representation of the BLE Service UUID
     * @param sessionUuid string representation of the BLE Session characteristic UUID
     * @param configUuidMap map of config paths and string representations of the BLE characteristic UUID
     * @param transportListener listener implementation which will receive resulting events
     */
    public BLETransport(Activity context,
                        UUID serviceUuid,
                        UUID sessionUuid,
                        HashMap<String, String> configUuidMap,
                        BLETransportListener transportListener) {
        this.context = context;
        this.serviceUuid = serviceUuid;
        this.sessionCharacteristicUuid = sessionUuid;
        this.configUUIDMap = configUuidMap;
        this.transportToken = new Semaphore(1);
        this.dispatcherThreadPool = Executors.newSingleThreadExecutor();
        this.transportListener = transportListener;
    }

    /***
     * BLE implementation of Transport protocol
     * @param data data to be sent
     * @param listener listener implementation which receives events when response is received.
     */
    @Override
    public void sendSessionData(byte[] data, ResponseListener listener) {
        if (sessionCharacteristic != null) {
            try {
                this.transportToken.acquire();
                sessionCharacteristic.setValue(data);
                bluetoothGatt.writeCharacteristic(sessionCharacteristic);
                currentResponseListener = listener;
            } catch (Exception e) {
                e.printStackTrace();
                listener.onFailure(e);
                this.transportToken.release();
            }
        }
    }

    /***
     * BLE implementation of Transport protocol
     * @param path path of the config endpoint.
     * @param data config data to be sent
     * @param listener listener implementation which receives events when response is received.
     */
    @Override
    public void sendConfigData(String path, byte[] data, ResponseListener listener) {
        try {
            this.transportToken.acquire();
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(configUUIDMap.get(path)));
            if (characteristic != null) {
                characteristic.setValue(data);
                bluetoothGatt.writeCharacteristic(characteristic);
                currentResponseListener = listener;
            } else {
                this.transportToken.release();
            }
        } catch (Exception e) {
            e.printStackTrace();
            listener.onFailure(e);
            this.transportToken.release();
        }
    }

    /***
     * Connect to a BLE peripheral device.
     * @param bluetoothDevice The peripheral device
     */
    public void connect(BluetoothDevice bluetoothDevice) {

        this.currentDevice = bluetoothDevice;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = this.currentDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            bluetoothGatt = this.currentDevice.connectGatt(context, false, gattCallback);
        }
    }

    /***
     * Disconnect from the current connected peripheral
     */
    public void disconnect() {
        if (this.bluetoothGatt != null) {
            this.bluetoothGatt.disconnect();
            bluetoothGatt = null;
        }
    }

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            super.onConnectionStateChange(gatt, status, newState);
            Log.d(TAG, "onConnectionStateChange, New state : " + newState + ", Status : " + status);

            if (status == BluetoothGatt.GATT_FAILURE) {
                if (transportListener != null) {
                    transportListener.onFailure(new Exception("GATT failure in connection"));
                }
                return;
            } else if (status != BluetoothGatt.GATT_SUCCESS) {
                return;
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server.");
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.e(TAG, "Disconnected from GATT server.");
                if (transportListener != null) {
                    transportListener.onPeripheralDisconnected(new Exception("Bluetooth device disconnected"));
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            super.onServicesDiscovered(gatt, status);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Status not success");
                return;
            }

            service = gatt.getService(serviceUuid);
            bluetoothGatt = gatt;

            if (service == null) {
                Log.e(TAG, "Service not found!");
                return;
            }

            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            }

            sessionCharacteristic = service.getCharacteristic(sessionCharacteristicUuid);

            if (transportListener != null) {

                if (sessionCharacteristic != null) {
                    // This is where provisionSession will get called.
                    Log.d(TAG, "Session characteristic not NULL " + currentDevice.getAddress());
                    transportListener.onPeripheralConfigured(currentDevice);
                } else {
                    Log.d(TAG, "Session characteristic is NULL");
                    transportListener.onPeripheralNotConfigured(currentDevice);
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Supported MTU = " + mtu);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged");
            super.onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         final BluetoothGattCharacteristic characteristic,
                                         int status) {

            Log.d(TAG, "onCharacteristicRead, status " + status + " UUID : " + characteristic.getUuid().toString());
            super.onCharacteristicRead(gatt, characteristic, status);

            if (currentResponseListener != null) {

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    /*
                     * Need to dispatch this on another thread since the caller
                     * might decide to enqueue another send operation on success
                     * of the first.
                     */
                    final ResponseListener responseListener = currentResponseListener;
                    dispatcherThreadPool.submit(new Runnable() {
                        @Override
                        public void run() {
                            byte[] charValue = characteristic.getValue();
                            responseListener.onSuccess(charValue);
                        }
                    });
                    currentResponseListener = null;
                } else {
                    currentResponseListener.onFailure(new Exception("Read from BLE failed"));
                }
            }
            transportToken.release();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

            Log.d(TAG, "onCharacteristicWrite, status : " + status);
            super.onCharacteristicWrite(gatt, characteristic, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                bluetoothGatt.readCharacteristic(characteristic);
            } else {
                if (currentResponseListener != null) {
                    currentResponseListener.onFailure(new Exception("Write to BLE failed"));
                }
                transportToken.release();
            }
        }
    };

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
