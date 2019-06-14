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
package com.espressif.ui.activities;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.espressif.provision.Provision;
import com.espressif.provision.R;
import com.espressif.provision.security.Security;
import com.espressif.provision.security.Security0;
import com.espressif.provision.security.Security1;
import com.espressif.provision.session.Session;
import com.espressif.provision.transport.BLETransport;
import com.espressif.AppConstants;
import com.espressif.ui.ProvisionActivity;
import com.espressif.ui.adapters.BleDeviceListAdapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;

public class BLEProvisionLanding extends AppCompatActivity {

    private static final String TAG = "Espressif::" + BLEProvisionLanding.class.getSimpleName();

    public static BLETransport bleTransport;
    public static boolean isBleWorkDone = false;

    private Button btnScan;
    private ListView listView;
    private ProgressBar progressBar;

    private BleDeviceListAdapter adapter;
    private BluetoothAdapter bleAdapter;
    private BLETransport.BLETransportListener transportListener;
    private ArrayList<BluetoothDevice> deviceList;
    private HashMap<BluetoothDevice, String> bluetoothDevices;

    private Session session;
    private Security security;
    // FIXME : Remove static BLE_TRANSPORT and think for another solution.

    private String deviceNamePrefix;
    private boolean isScanning = false, isDeviceConnected = false, isConnecting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bleprovision_landing);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.connect_to_device_title);
        setSupportActionBar(toolbar);

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.error_ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bleAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (bleAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        isConnecting = false;
        isDeviceConnected = false;
        bluetoothDevices = new HashMap<>();
        deviceNamePrefix = getIntent().getStringExtra(BLETransport.DEVICE_NAME_PREFIX_KEY);

        btnScan = findViewById(R.id.btn_scan);
        listView = findViewById(R.id.ble_devices_list);
        progressBar = findViewById(R.id.ble_landing_progress_indicator);

        Collection<BluetoothDevice> keySet = bluetoothDevices.keySet();
        deviceList = new ArrayList<>(keySet);

        adapter = new BleDeviceListAdapter(this, R.layout.item_ble_scan, deviceList);

        // Assign adapter to ListView
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(btDeviceCLickListener);

        btnScan.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {

                Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                vib.vibrate(HapticFeedbackConstants.VIRTUAL_KEY);
                bluetoothDevices.clear();
                adapter.clear();
                startScan();
            }
        });

        transportListener = new BLETransport.BLETransportListener() {

            @Override
            public void onPeripheralFound(BluetoothDevice device, String serviceUuid) {

                Log.d(TAG, "====== onPeripheralFound =====");
                boolean deviceExists = false;

                Log.d(TAG, "Add service UUID : " + serviceUuid);

                if (bluetoothDevices.containsKey(device)) {
                    deviceExists = true;
                }

                if (!deviceExists) {
                    listView.setVisibility(View.VISIBLE);
                    bluetoothDevices.put(device, serviceUuid);
                    deviceList.add(device);
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onPeripheralsFound(HashMap<BluetoothDevice, String> devices) {

                // No need to add devices again as they are already received in onPeripheralFound
                adapter.notifyDataSetChanged();
                scanningStopAction();
            }

            @Override
            public void onPeripheralsNotFound() {
                scanningStopAction();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(BLEProvisionLanding.this,
                                "No Bluetooth devices found!",
                                Toast.LENGTH_LONG)
                                .show();
                    }
                });
            }

            @Override
            public void onPeripheralConfigured(BluetoothDevice device) {
                bleDeviceConfigured(true);
            }

            @Override
            public void onPeripheralNotConfigured(BluetoothDevice device) {
                bleDeviceConfigured(false);
            }

            @Override
            public void onPeripheralDisconnected(Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(BLEProvisionLanding.this,
                                "Bluetooth device disconnected.",
                                Toast.LENGTH_LONG)
                                .show();
                    }
                });
            }

            @Override
            public void onFailure(final Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(BLEProvisionLanding.this,
                                "Bluetooth connection failed : " + e.getMessage(),
                                Toast.LENGTH_LONG)
                                .show();
                    }
                });
            }
        };

        bleTransport = new BLETransport(this,
                deviceNamePrefix,
                3000);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "ON RESUME");

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!bleAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, AppConstants.REQUEST_ENABLE_BT);
        } else {
            if (!isDeviceConnected && !isConnecting) {
                startScan();
            }

            if (isBleWorkDone) {
                bleTransport.disconnect();
                btnScan.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onBackPressed() {
        Log.e(TAG, "ON BACK PRESSED");
        isBleWorkDone = true;
        bleTransport.disconnect();
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        Log.e(TAG, "onActivityResult, requestCode : " + requestCode + ", resultCode : " + resultCode);
        // User chose not to enable Bluetooth.
        if (requestCode == AppConstants.REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }

        if (requestCode == Provision.REQUEST_PROVISIONING_CODE && resultCode == RESULT_OK) {
            setResult(resultCode);
            finish();
        } else if (requestCode == AppConstants.REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            startScan();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {

        Log.e(TAG, "onRequestPermissionsResult : requestCode : " + requestCode);

        switch (requestCode) {

            case AppConstants.REQUEST_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startScan();
                }
            }
            break;
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
        startActivityForResult(enableBtIntent, AppConstants.REQUEST_ENABLE_BT);
        Log.d(TAG, "Requested user enables Bluetooth. Try starting the scan again.");
    }

    private boolean hasLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, AppConstants.REQUEST_FINE_LOCATION);
        }
    }

    private void startScan() {
        if (!hasPermissions() || isScanning) {
            return;
        }

        Log.e(TAG, "Start Scanning...");
        bleTransport.disconnect();

        isScanning = true;
        updateProgressAndScanBtn();
        bleTransport.scan(transportListener);
    }

    private void scanningStopAction() {

        Log.e(TAG, "Stop Scanning...");
        isScanning = false;
        updateProgressAndScanBtn();
    }

    private void bleDeviceConfigured(final Boolean isConfigured) {

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (isConfigured) {

                    isDeviceConnected = true;
//                    finish();
//                    goToProofOfPossessionActivity();
                    initSession();

                } else {
                    Toast.makeText(BLEProvisionLanding.this,
                            "Bluetooth device could not be configured. Please try another device.",
                            Toast.LENGTH_LONG)
                            .show();
                }
            }
        });
    }

    /**
     * This method will update UI (Scan button enable / disable and progressbar visibility)
     */
    private void updateProgressAndScanBtn() {

        if (isScanning) {

            btnScan.setEnabled(false);
            btnScan.setAlpha(0.5f);
            btnScan.setTextColor(Color.WHITE);
            progressBar.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);

        } else {

            btnScan.setEnabled(true);
            btnScan.setAlpha(1f);
            progressBar.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }
    }

    private void goToProofOfPossessionActivity() {

        Intent alexaProvisioningIntent = new Intent(getApplicationContext(), ProofOfPossessionActivity.class);
        alexaProvisioningIntent.putExtras(getIntent());
        alexaProvisioningIntent.putExtra(AppConstants.KEY_IS_PROVISIONING, true);
        startActivity(alexaProvisioningIntent);
    }

    private void goToPopActivity() {

        Intent alexaProvisioningIntent = new Intent(getApplicationContext(), ProofOfPossessionActivity.class);
        alexaProvisioningIntent.putExtras(getIntent());
        alexaProvisioningIntent.putExtra(AppConstants.KEY_IS_PROVISIONING, true);
        startActivity(alexaProvisioningIntent);
    }

    private void goToProvisionActivity() {

        Intent launchProvisionInstructions = new Intent(getApplicationContext(), ProvisionActivity.class);
        launchProvisionInstructions.putExtras(getIntent());
        launchProvisionInstructions.putExtra(AppConstants.KEY_IS_PROVISIONING, true);
        launchProvisionInstructions.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, "");
        startActivityForResult(launchProvisionInstructions, Provision.REQUEST_PROVISIONING_CODE);
    }

    private void initSession() {

        final String pop = "";
        final String securityVersion = getIntent().getStringExtra(Provision.CONFIG_SECURITY_KEY);

        if (bleTransport.deviceCapabilities.contains("no_pop")) {

            if (securityVersion.equals(Provision.CONFIG_SECURITY_SECURITY1)) {
                security = new Security1(pop);
            } else {
                security = new Security0();
            }

            session = new Session(BLEProvisionLanding.bleTransport, security);

            session.sessionListener = new Session.SessionListener() {

                @Override
                public void OnSessionEstablished() {
                    Log.d(TAG, "Session established");
                    goToProvisionActivity();
                }

                @Override
                public void OnSessionEstablishFailed(Exception e) {
                    Log.d(TAG, "Session failed");
                }
            };
            session.init(null);

        } else {

            if (securityVersion.equals(Provision.CONFIG_SECURITY_SECURITY1)) {

                goToPopActivity();

            } else {

                security = new Security0();
                session = new Session(BLEProvisionLanding.bleTransport, security);

                session.sessionListener = new Session.SessionListener() {

                    @Override
                    public void OnSessionEstablished() {
                        Log.d(TAG, "Session established");

                        goToProvisionActivity();
                    }

                    @Override
                    public void OnSessionEstablishFailed(Exception e) {
                        Log.d(TAG, "Session failed");
                    }
                };
                session.init(null);
            }
        }
    }

    private AdapterView.OnItemClickListener btDeviceCLickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {

            // TODO Stop scanning.
            isConnecting = true;
            isDeviceConnected = false;
            btnScan.setVisibility(View.GONE);
            listView.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);
            BluetoothDevice device = adapter.getItem(position);
            String uuid = bluetoothDevices.get(device);
            Log.e(TAG, "=================== Connect to device : " + device.getName() + " UUID : " + uuid);

            bleTransport.connect(device, UUID.fromString(uuid));
        }
    };
}
