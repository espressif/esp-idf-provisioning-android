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

import com.espressif.AppConstants;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class BLETransportLegacy extends BLETransport {

    private static final String TAG = "Espressif::" + BLETransportLegacy.class.getSimpleName();

    private static final String SERVICE_UUID = "0000ffff-0000-1000-8000-00805f9b34fb";

    private static final String PROV_SCAN_UUID = "0000ff50-0000-1000-8000-00805f9b34fb";
    private static final String PROV_SESSION_UUID = "0000ff51-0000-1000-8000-00805f9b34fb";
    private static final String PROV_CONFIG_UUID = "0000ff52-0000-1000-8000-00805f9b34fb";
    private static final String PROTO_VER_UUID = "0000ff53-0000-1000-8000-00805f9b34fb";
    private static final String AVS_CONFIG_UUID = "0000ff54-0000-1000-8000-00805f9b34fb";

    private Activity context;
    private BluetoothDevice currentDevice;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattService service;
    private BluetoothGattCharacteristic sessionCharacteristic;
    private UUID serviceUuid;
    private Semaphore transportToken;
    private ExecutorService dispatcherThreadPool;
    private BLETransportListener transportListener;
    private ResponseListener currentResponseListener;
    private HashMap<String, String> configUUIDMap;

    public BLETransportLegacy(Activity context, BLETransportListener transportListener) {

        this.context = context;
        this.transportToken = new Semaphore(1);
        this.dispatcherThreadPool = Executors.newSingleThreadExecutor();
        this.transportListener = transportListener;

        configUUIDMap = new HashMap<>();
        configUUIDMap.put(AppConstants.HANDLER_PROV_SCAN, PROV_SCAN_UUID);
        configUUIDMap.put(AppConstants.HANDLER_PROV_SESSION, PROV_SESSION_UUID);
        configUUIDMap.put(AppConstants.HANDLER_PROV_CONFIG, PROV_CONFIG_UUID);
        configUUIDMap.put(AppConstants.HANDLER_PROTO_VER, PROTO_VER_UUID);
        configUUIDMap.put(AppConstants.HANDLER_AVS_CONFIG, AVS_CONFIG_UUID);
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

    @Override
    public void connect(BluetoothDevice bluetoothDevice, UUID primaryServiceUuid) {
        this.currentDevice = bluetoothDevice;
        this.serviceUuid = primaryServiceUuid;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            bluetoothGatt = this.currentDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            bluetoothGatt = this.currentDevice.connectGatt(context, false, gattCallback);
        }
    }

    @Override
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
                Log.e(TAG, "Connected to GATT server.");
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
                return;
            }
            service = gatt.getService(serviceUuid);
            bluetoothGatt = gatt;
//            bluetoothGatt.requestMtu(400);

            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            }

            sessionCharacteristic = service.getCharacteristic(UUID.fromString(PROV_SESSION_UUID));
            if (transportListener != null) {
                if (sessionCharacteristic != null) {
                    transportListener.onPeripheralConfigured(currentDevice);
                } else {
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
                    /**
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
            Log.d(TAG, "UUID : " + characteristic.getUuid().toString());
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
}
