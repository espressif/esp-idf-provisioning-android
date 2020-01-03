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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiClient;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.provision.BuildConfig;
import com.espressif.provision.Provision;
import com.espressif.provision.R;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.provision.transport.SoftAPTransport;
import com.espressif.ui.adapters.EspDeviceAdapter;
import com.espressif.ui.models.EspDevice;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.UpdateEvent;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.notbytes.barcode_reader.BarcodeReaderActivity;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class EspMainActivity extends AppCompatActivity {

    private static final String TAG = "Espressif::" + EspMainActivity.class.getSimpleName();

    private static final int REQUEST_LOCATION = 1;
    private static final int BARCODE_READER_ACTIVITY_REQUEST = 120;

    private FloatingActionButton btnProvision, btnScanQrCode;
    private ProgressDialog progressDialog;
    private RecyclerView recyclerView;
    private TextView tvNoDevice, tvTitleDevices;
    private SwipeRefreshLayout swipeRefreshLayout;

    private String BASE_URL;
    private String transportVersion, securityVersion;
    private ApiManager apiManager;
    private EspApplication espApp;
    private EspDeviceAdapter deviceAdapter;
    private ArrayList<EspDevice> devices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esp_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        devices = new ArrayList<>();
        BASE_URL = getResources().getString(R.string.wifi_base_url);
        espApp = (EspApplication) getApplicationContext();
        initViews();

        SharedPreferences sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        String wifiNamePrefix = sharedPreferences.getString(AppConstants.KEY_WIFI_NETWORK_NAME_PREFIX, "");
        String bleDevicePrefix = sharedPreferences.getString(AppConstants.KEY_BLE_DEVICE_NAME_PREFIX, "");

        if (TextUtils.isEmpty(wifiNamePrefix)) {

            wifiNamePrefix = getResources().getString(R.string.wifi_network_name_prefix);
            editor.putString(AppConstants.KEY_WIFI_NETWORK_NAME_PREFIX, wifiNamePrefix);
        }

        if (TextUtils.isEmpty(bleDevicePrefix)) {

            bleDevicePrefix = getResources().getString(R.string.ble_device_name_prefix);
            editor.putString(AppConstants.KEY_BLE_DEVICE_NAME_PREFIX, bleDevicePrefix);
        }

        editor.apply();

        if (BuildConfig.FLAVOR_security.equals("sec1")) {
            securityVersion = Provision.CONFIG_SECURITY_SECURITY1;
        } else {
            securityVersion = Provision.CONFIG_SECURITY_SECURITY0;
        }

        if (BuildConfig.FLAVOR_transport.equals("ble")) {
            transportVersion = Provision.CONFIG_TRANSPORT_BLE;
        } else {
            transportVersion = Provision.CONFIG_TRANSPORT_WIFI;
        }

        tvNoDevice.setVisibility(View.GONE);
        tvTitleDevices.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);

        showWaitDialog("Getting devices...");
        apiManager = new ApiManager(getApplicationContext());
        getSupportedVersions();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (BuildConfig.FLAVOR_transport.equals("ble") && BLEProvisionLanding.isBleWorkDone) {
            BLEProvisionLanding.bleTransport.disconnect();
        }
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UpdateEvent event) {
        showWaitDialog("Getting devices...");
        getDevices();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult, resultCode : " + resultCode + ", requestCode : " + requestCode);

        if (requestCode == REQUEST_LOCATION) {

            Log.d(TAG, "REQUEST_LOCATION result received");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                if (isLocationEnabled()) {

                    HashMap<String, String> config = new HashMap<>();
                    config.put(Provision.CONFIG_TRANSPORT_KEY, transportVersion);
                    config.put(Provision.CONFIG_SECURITY_KEY, securityVersion);
                    config.put(Provision.CONFIG_BASE_URL_KEY, BASE_URL);

                    Provision.showProvisioningUI(EspMainActivity.this, config);
                }
            }
        } else if (requestCode == BARCODE_READER_ACTIVITY_REQUEST) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                if (!isLocationEnabled()) {
                    askForLocation();
                    return;
                }
            }

            if (resultCode != RESULT_OK) {
                Toast.makeText(this, "Error in  scanning", Toast.LENGTH_SHORT).show();
                return;
            }

            if (data != null) {

                Log.e(TAG, "Data is not null");
                final String deviceName = data.getStringExtra("deviceName");
                String password = data.getStringExtra("password");
                final String pop = data.getStringExtra("pop");
                String transportValue = data.getStringExtra("transport");

                Log.e(TAG, "deviceName : " + deviceName + " password : " + password + " pop : " + pop + " transportValue : " + transportValue);
                ProvisionLanding.softAPTransport = new SoftAPTransport(BASE_URL);
                String tempData = "ESP";

                ProvisionLanding.softAPTransport.sendConfigData(AppConstants.HANDLER_PROTO_VER, tempData.getBytes(), new ResponseListener() {

                    @Override
                    public void onSuccess(byte[] returnData) {

                        String data = new String(returnData, StandardCharsets.UTF_8);
                        Log.d(TAG, "Value : " + data);

                        try {
                            JSONObject jsonObject = new JSONObject(data);
                            JSONObject provInfo = jsonObject.getJSONObject("prov");

                            String versionInfo = provInfo.getString("ver");
                            Log.d(TAG, "Device Version : " + versionInfo);

                            JSONArray capabilities = provInfo.getJSONArray("cap");
                            ProvisionLanding.deviceCapabilities = new ArrayList<>();

                            for (int i = 0; i < capabilities.length(); i++) {
                                String cap = capabilities.getString(i);
                                ProvisionLanding.deviceCapabilities.add(cap);
                            }
                            Log.d(TAG, "Capabilities : " + ProvisionLanding.deviceCapabilities);

                        } catch (JSONException e) {
                            e.printStackTrace();
                            Log.d(TAG, "Capabilities JSON not available.");
                        }

                        if (!ProvisionLanding.deviceCapabilities.contains("no_pop") && securityVersion.equals(Provision.CONFIG_SECURITY_SECURITY1)) {

                            if (!TextUtils.isEmpty(pop)) {
                                goToWifiScanListActivity(pop);
                            } else {
                                goToPopActivity(deviceName);
                            }

                        } else if (ProvisionLanding.deviceCapabilities.contains("wifi_scan")) {

                            goToWifiScanListActivity("");

                        } else {

                            if (!TextUtils.isEmpty(pop)) {
                                goToProvisionActivity(pop);
                            } else {
                                goToProvisionActivity("");
                            }
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    View.OnClickListener provisionBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vib.vibrate(HapticFeedbackConstants.VIRTUAL_KEY);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                if (!isLocationEnabled()) {
                    askForLocation();
                    return;
                }
            }

            HashMap<String, String> config = new HashMap<>();
            config.put(Provision.CONFIG_TRANSPORT_KEY, transportVersion);
            config.put(Provision.CONFIG_SECURITY_KEY, securityVersion);
            config.put(Provision.CONFIG_BASE_URL_KEY, BASE_URL);

            Provision.showProvisioningUI(EspMainActivity.this, config);
        }
    };

    View.OnClickListener scanQrCodeBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vib.vibrate(HapticFeedbackConstants.VIRTUAL_KEY);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                if (!isLocationEnabled()) {
                    askForLocation();
                    return;
                }
            }
            ((EspApplication) getApplicationContext()).enableOnlyWifiNetwork();
            goToBarCodeActivity();
        }
    };

    private void initViews() {

        btnProvision = findViewById(R.id.btn_provision);
        btnScanQrCode = findViewById(R.id.btn_scan_qr_code);
        tvNoDevice = findViewById(R.id.tv_no_device);
        tvTitleDevices = findViewById(R.id.txt_devices);
        recyclerView = findViewById(R.id.rv_device_list);
        swipeRefreshLayout = findViewById(R.id.swipe_container);
        btnProvision.setOnClickListener(provisionBtnClickListener);
        btnScanQrCode.setOnClickListener(scanQrCodeBtnClickListener);

        // set a LinearLayoutManager with default orientation
        GridLayoutManager linearLayoutManager = new GridLayoutManager(getApplicationContext(), 2);
        recyclerView.setLayoutManager(linearLayoutManager); // set LayoutManager to RecyclerView

        deviceAdapter = new EspDeviceAdapter(this, devices);
        recyclerView.setAdapter(deviceAdapter);

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {

            @Override
            public void onRefresh() {
                getDevices();
            }
        });
    }

    private void getSupportedVersions() {

        apiManager.getSupportedVersions(new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                String updateMsg = data.getString("additional_info");
                ArrayList<String> versions = data.getStringArrayList("supported_versions");

                if (!versions.contains(ApiClient.CURRENT_VERSION)) {
                    alertForForceUpdate(updateMsg);
                } else {

                    int currentVersion = getVersionNumber(ApiClient.CURRENT_VERSION);

                    for (int i = 0; i < versions.size(); i++) {

                        int version = getVersionNumber(versions.get(i));

                        if (version > currentVersion) {

                            // TODO Make flag true once alert is shown so that update popup will not come every time.
                            if (!TextUtils.isEmpty(updateMsg)) {
                                alertForNewVersion(updateMsg);
                            } else {
                                alertForNewVersion("");
                            }
                            break;
                        }
                    }
                }

                getUserId();
            }

            @Override
            public void onFailure(Exception exception) {
                Toast.makeText(EspMainActivity.this, "Failed to get Devices", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int getVersionNumber(String versionString) {

        versionString = versionString.replace("v", "");
        int version = Integer.valueOf(versionString);
        return version;
    }

    private void getUserId() {

        apiManager.getUserId(ApiManager.userName, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                getDevices();
            }

            @Override
            public void onFailure(Exception exception) {
                closeWaitDialog();
                Toast.makeText(EspMainActivity.this, "Failed to get Devices", Toast.LENGTH_SHORT).show();
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void getDevices() {

        Log.e(TAG, "Get devices");
        apiManager.getAllDevices(new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                Log.e(TAG, "Get devices Success received");
                closeWaitDialog();
                swipeRefreshLayout.setRefreshing(false);
                devices.clear();

                for (Map.Entry<String, EspNode> entry : espApp.nodeMap.entrySet()) {

                    String key = entry.getKey();
                    EspNode node = entry.getValue();

                    if (node != null) {
                        ArrayList<EspDevice> espDevices = node.getDevices();
                        devices.addAll(espDevices);
                        Log.d(TAG, "Devices size : " + espDevices.size());
                    }
                }

                Log.d(TAG, "Device list size : " + devices.size());

                if (devices.size() > 0) {

                    tvNoDevice.setVisibility(View.GONE);
                    tvTitleDevices.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.VISIBLE);

                } else {
                    tvNoDevice.setVisibility(View.VISIBLE);
                    tvTitleDevices.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.GONE);
                }
                deviceAdapter.updateList(devices);
            }

            @Override
            public void onFailure(Exception exception) {
                exception.printStackTrace();
                closeWaitDialog();
                Toast.makeText(EspMainActivity.this, "Failed to get Devices", Toast.LENGTH_SHORT).show();
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void goToBarCodeActivity() {

        Intent launchIntent = BarcodeReaderActivity.getLaunchIntent(this, true, false);
        startActivityForResult(launchIntent, BARCODE_READER_ACTIVITY_REQUEST);
    }

    private void alertForForceUpdate(String message) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);

        builder.setTitle("New Version is available!");
        builder.setMessage(message);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_update, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                final String appPackageName = getPackageName(); // getPackageName() from Context or Activity object
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                } catch (android.content.ActivityNotFoundException anfe) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                }
            }
        });

        builder.show();
    }

    private void alertForNewVersion(String message) {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);

        builder.setTitle("New Version is available!");
        builder.setMessage(message);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_update, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                final String appPackageName = getPackageName(); // getPackageName() from Context or Activity object
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                } catch (android.content.ActivityNotFoundException anfe) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                }
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });

        builder.show();
    }

    private void askForLocation() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(R.string.dialog_msg_for_gps);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_LOCATION);
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private boolean isLocationEnabled() {

        boolean gps_enabled = false;
        boolean network_enabled = false;
        LocationManager lm = (LocationManager) getApplicationContext().getSystemService(Activity.LOCATION_SERVICE);

        try {
            gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ex) {
        }

        try {
            network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        } catch (Exception ex) {
        }

        Log.d(TAG, "GPS Enabled : " + gps_enabled + " , Network Enabled : " + network_enabled);

        boolean result = gps_enabled || network_enabled;
        return result;
    }

    private void showWaitDialog(String message) {

        closeWaitDialog();

        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
        }
        progressDialog.setTitle(message);
        progressDialog.setCancelable(true);
        progressDialog.show();
    }

    private void closeWaitDialog() {
        try {
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void goToPopActivity(String currentSSID) {

        Intent popIntent = new Intent(getApplicationContext(), ProofOfPossessionActivity.class);
        putExtraInformation(popIntent);
        popIntent.putExtra(AppConstants.KEY_DEVICE_NAME, currentSSID);
        startActivity(popIntent);
    }

    private void goToWifiScanListActivity(String pop) {

        Intent wifiListIntent = new Intent(getApplicationContext(), WiFiScanActivity.class);
        putExtraInformation(wifiListIntent);
        wifiListIntent.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, pop);
        startActivity(wifiListIntent);
    }

    private void goToProvisionActivity(String pop) {

        Intent provisionIntent = new Intent(getApplicationContext(), ProvisionActivity.class);
        putExtraInformation(provisionIntent);
        provisionIntent.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, pop);
        startActivityForResult(provisionIntent, Provision.REQUEST_PROVISIONING_CODE);
    }

    private void putExtraInformation(Intent intent) {

        Bundle optionsBundle = new Bundle();
        optionsBundle.putString(Provision.CONFIG_TRANSPORT_KEY, transportVersion);
        optionsBundle.putString(Provision.CONFIG_SECURITY_KEY, securityVersion);
        optionsBundle.putString(Provision.CONFIG_BASE_URL_KEY, BASE_URL);
        intent.putExtras(optionsBundle);
    }
}
