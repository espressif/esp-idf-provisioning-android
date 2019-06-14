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

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;

import com.espressif.AppConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Bluetooth implementation of the Transport protocol.
 */
public class BLETransport implements Transport {

    private static final String TAG = "Espressif::" + BLETransport.class.getSimpleName();

    public static final String DEVICE_NAME_PREFIX_KEY = "deviceNamePrefix";

    private Activity context;
    private String deviceNamePrefix;
    private BluetoothAdapter bleAdapter;
    private HashMap<BluetoothDevice, String> bluetoothDevices;
    private long scanTimeoutInMillis;
    private BluetoothDevice currentDevice;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattService service;
    private BLETransportListener transportListener;
    private ResponseListener currentResponseListener;
    private Semaphore transportToken;
    private ExecutorService dispatcherThreadPool;
    private HashMap<String, String> uuidMap = new HashMap<>();
    private ArrayList<String> charUuidList = new ArrayList<>();

    private String serviceUuid;
    private boolean isReadingDescriptors = false;
    public ArrayList<String> deviceCapabilities = new ArrayList<>();

    /***
     * Create BLETransport implementation
     * @param context
     * @param deviceNamePrefix device name prefix
     * @param scanTimeoutInMillis timeout in milliseconds for which BLE scan should happen
     */
    public BLETransport(Activity context,
                        String deviceNamePrefix,
                        long scanTimeoutInMillis) {
        this.context = context;
        this.deviceNamePrefix = deviceNamePrefix;
        this.transportToken = new Semaphore(1);
        this.dispatcherThreadPool = Executors.newSingleThreadExecutor();
        this.bluetoothDevices = new HashMap<>();
        this.scanTimeoutInMillis = scanTimeoutInMillis;
    }

    /***
     * BLE implementation of Transport protocol
     * @param data data to be sent
     * @param listener listener implementation which receives events when response is received.
     */
    @Override
    public void sendSessionData(byte[] data, ResponseListener listener) {

        if (uuidMap.containsKey(AppConstants.HANDLER_PROV_SESSION)) {

            BluetoothGattCharacteristic sessionCharacteristic = service.getCharacteristic(UUID.fromString(uuidMap.get(AppConstants.HANDLER_PROV_SESSION)));

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
            } else {
                Log.e(TAG, "Session Characteristic is not available.");
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

        if (uuidMap.containsKey(path)) {

            BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(uuidMap.get(path)));

            if (characteristic != null) {
                try {
                    this.transportToken.acquire();
                    characteristic.setValue(data);
                    bluetoothGatt.writeCharacteristic(characteristic);
                    currentResponseListener = listener;
                } catch (Exception e) {
                    e.printStackTrace();
                    listener.onFailure(e);
                    this.transportToken.release();
                }
            } else {
                Log.e(TAG, "Characteristic is not available for given path.");
            }
        }
    }

    /***
     * Scan for BLE bluetoothDevices
     * @param transportListener listener implementation which will receive resulting events
     */
    public void scan(BLETransportListener transportListener) {
        this.transportListener = transportListener;
        this.scanForPeripherals();
    }

    /***
     * Connect to a BLE peripheral device.
     * @param bluetoothDevice The peripheral device
     * @param primaryServiceUuid Primary Service UUID
     */
    public void connect(BluetoothDevice bluetoothDevice, UUID primaryServiceUuid) {
        this.currentDevice = bluetoothDevice;
        this.serviceUuid = primaryServiceUuid.toString();
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

    private void scanForPeripherals() {

        final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        bleAdapter = bluetoothManager.getAdapter();
        bluetoothDevices.clear();

        if (!this.hasPermissions()) {
            if (transportListener != null) {
                transportListener.onFailure(new Exception("Not enough permissions to connect over BLE"));
            }
        } else {
            final BluetoothLeScanner scanner = bleAdapter.getBluetoothLeScanner();
            List<ScanFilter> filters = new ArrayList<>();
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                    .build();

            final ScanCallback scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {

                    boolean deviceExists = false;

                    String deviceName = result.getDevice().getName();
                    List<ParcelUuid> uuids = result.getScanRecord().getServiceUuids();

                    if (!TextUtils.isEmpty(deviceName)) {
                        if (bluetoothDevices.containsKey(result.getDevice())) {
                            deviceExists = true;
                            bluetoothDevices.put(result.getDevice(), uuids.get(0).toString());
                        }
                    } else {
                        Log.e(TAG, "Device is empty");
                    }

                    if (!deviceExists) {

                        if (!TextUtils.isEmpty(deviceName) && deviceName.startsWith(deviceNamePrefix)) {

                            if (uuids != null && uuids.size() > 0 && !TextUtils.isEmpty(deviceName)) {

                                Log.d(TAG, "Add device : " + deviceName);
                                bluetoothDevices.put(result.getDevice(), uuids.get(0).toString());
                            }
                        }
                    }

                    if (uuids != null && uuids.size() > 0 && !TextUtils.isEmpty(deviceName)) {
                        transportListener.onPeripheralFound(result.getDevice(), uuids.get(0).toString());
                    }
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    super.onBatchScanResults(results);
                }

                @Override
                public void onScanFailed(int errorCode) {
                    super.onScanFailed(errorCode);
                    if (transportListener != null) {
                        transportListener.onFailure(new Exception("BLE connect failed with error code : " + errorCode));
                    }
                }
            };

            scanner.startScan(filters, settings, scanCallback);
            Handler someHandler = new Handler();
            someHandler.postDelayed(new

                                            Runnable() {
                                                @Override
                                                public void run() {
                                                    if (bluetoothDevices.size() == 0 && transportListener != null) {
                                                        transportListener.onPeripheralsNotFound();
                                                    } else if (transportListener != null) {
                                                        transportListener.onPeripheralsFound(bluetoothDevices);
                                                    }
                                                    scanner.stopScan(scanCallback);
                                                    bluetoothDevices.clear();
                                                }
                                            }, this.scanTimeoutInMillis);
        }
    }

    private boolean hasPermissions() {
        if (bleAdapter == null || !bleAdapter.isEnabled()) {
            requestBluetoothEnable();
            return false;
        } else if (!hasLocationPermissions()) {
            requestLocationPermission();
            return false;
        }
        return true;
    }

    private void requestBluetoothEnable() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        context.startActivityForResult(enableBtIntent, AppConstants.REQUEST_ENABLE_BT);
    }

    private boolean hasLocationPermissions() {
        return ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(context,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, AppConstants.REQUEST_FINE_LOCATION);
    }

    private BluetoothGattCallback gattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            super.onConnectionStateChange(gatt, status, newState);
            Log.e(TAG, "onConnectionStateChange, New state : " + newState + ", Status : " + status);

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
                Log.d(TAG, "Status not success");
                return;
            }

            service = gatt.getService(UUID.fromString(serviceUuid));

            if (service == null) {
                Log.e(TAG, "Service not found!");
                return;
            }

            for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {

                if (characteristic == null) {
                    Log.e(TAG, "Tx characteristic not found!");
                    return;
                }

                String uuid = characteristic.getUuid().toString();
                Log.e(TAG, "Characteristic UUID : " + uuid);
                charUuidList.add(uuid);

                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            }

            readNextDescriptor();
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Read Descriptor : " + bluetoothGatt.readDescriptor(descriptor));
            } else {
                Log.e(TAG, "Fail to read descriptor");
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

            Log.e(TAG, "DescriptorRead, : Status " + status + " Data : " + new String(descriptor.getValue(), StandardCharsets.UTF_8));

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Failed to read descriptor");
                return;
            }

            byte[] data = descriptor.getValue();

            String value = new String(data, StandardCharsets.UTF_8);
            uuidMap.put(value, descriptor.getCharacteristic().getUuid().toString());
            Log.e(TAG, "Value : " + value + " for UUID : " + descriptor.getCharacteristic().getUuid().toString());

            if (isReadingDescriptors) {

                readNextDescriptor();

            } else {

                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(uuidMap.get(AppConstants.HANDLER_PROTO_VER)));

                if (characteristic != null) {
                    // Write anything. It doesn't matter. We need to read characteristic and for that we need to write something.
                    characteristic.setValue("ESP");
                    bluetoothGatt.writeCharacteristic(characteristic);
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
            Log.e(TAG, "onCharacteristicChanged");
            super.onCharacteristicChanged(gatt, characteristic);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         final BluetoothGattCharacteristic characteristic,
                                         int status) {

            Log.e(TAG, "onCharacteristicRead, status " + status + " UUID : " + characteristic.getUuid().toString());
            super.onCharacteristicRead(gatt, characteristic, status);

            if (uuidMap.get((AppConstants.HANDLER_PROTO_VER)).equals(characteristic.getUuid().toString())) {

                String data = new String(characteristic.getValue(), StandardCharsets.UTF_8);
                Log.e(TAG, "Value : " + data);

                try {
                    JSONObject jsonObject = new JSONObject(data);
                    JSONObject provInfo = jsonObject.getJSONObject("prov");

                    String versionInfo = provInfo.getString("ver");
                    Log.d(TAG, "Device Version : " + versionInfo);

                    JSONArray capabilities = provInfo.getJSONArray("cap");

                    for (int i = 0; i < capabilities.length(); i++) {
                        String cap = capabilities.getString(i);
                        deviceCapabilities.add(cap);
                    }
                    Log.e(TAG, "Cap : " + deviceCapabilities);

                } catch (JSONException e) {
                    e.printStackTrace();
                }

                if (transportListener != null) {

                    if (uuidMap.containsKey(AppConstants.HANDLER_PROV_SESSION)) {
                        // This is where provisionSession will get called.
                        Log.d(TAG, "Session characteristic not NULL " + currentDevice.getAddress());
                        transportListener.onPeripheralConfigured(currentDevice);
                    } else {
                        Log.d(TAG, "Session characteristic is NULL");
                        transportListener.onPeripheralNotConfigured(currentDevice);
                    }
                }
            }

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

            Log.e(TAG, "onCharacteristicWrite, status : " + status);
            Log.e(TAG, "UUID : " + characteristic.getUuid().toString());
//            super.onCharacteristicWrite(gatt, characteristic, status);

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

    private void readNextDescriptor() {

        boolean found = false;

        for (int i = 0; i < charUuidList.size(); i++) {

            String uuid = charUuidList.get(i);

            if (!uuidMap.containsValue(uuid)) {

                // Read descriptor
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(uuid));
                if (characteristic == null) {
                    Log.e(TAG, "Tx characteristic not found!");
                    return;
                }

                for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {

                    Log.e(TAG, "Descriptor : " + descriptor.getUuid().toString());
                    Log.e(TAG, "Des read : " + bluetoothGatt.readDescriptor(descriptor));
                }
                found = true;
                break;
            }
        }

        if (found) {
            isReadingDescriptors = true;
        } else {

            isReadingDescriptors = false;

            BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(uuidMap.get(AppConstants.HANDLER_PROTO_VER)));

            if (characteristic != null) {
                characteristic.setValue("ESP");
                bluetoothGatt.writeCharacteristic(characteristic);
            }
        }
    }

    /***
     * Listener which will receive events relating to BLE device scanning
     */
    public interface BLETransportListener {

        /***
         * Peripheral bluetoothDevice found with matching Service UUID
         * Callers should call the BLETransport.connect method with
         * one of the peripheral found here
         * @param device
         * @param serviceUuid
         */
        void onPeripheralFound(BluetoothDevice device, String serviceUuid);

        /***
         * Peripheral bluetoothDevices found with matching Service UUID
         * Callers should call the BLETransport.connect method with
         * one of the peripherals found here
         * @param devices
         */
        void onPeripheralsFound(HashMap<BluetoothDevice, String> devices);

        /***
         * No peripherals found with matching Service UUID
         */
        void onPeripheralsNotFound();

        /***
         * Peripheral device configured.
         * This tells the caller that the connected BLE device is now configured
         * and can be provisioned
         * @param device
         */
        void onPeripheralConfigured(BluetoothDevice device);

        /***
         * Peripheral device could not be configured.
         * This tells the called that the connected device cannot be configured for provisioning
         * @param device
         */
        void onPeripheralNotConfigured(BluetoothDevice device);

        /***
         * Peripheral device disconnected
         * @param e
         */
        void onPeripheralDisconnected(Exception e);

        /***
         * Failed to scan for BLE bluetoothDevices
         * @param e
         */
        void onFailure(Exception e);
    }
}
