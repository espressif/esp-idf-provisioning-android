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
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.espressif.AppConstants;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.provision.BuildConfig;
import com.espressif.provision.Provision;
import com.espressif.provision.R;
import com.espressif.ui.adapters.EspDeviceAdapter;
import com.espressif.ui.user_module.EspDevice;

import java.util.ArrayList;
import java.util.HashMap;

public class EspMainActivity extends AppCompatActivity {

    private static final String TAG = "Espressif::" + EspMainActivity.class.getSimpleName();

    private static final int REQUEST_LOCATION = 1;

    private Button btnProvision;
    private ProgressDialog progressDialog;
    private RecyclerView recyclerView;
    private TextView tvNoDevice, tvTitleDevices;
    private SwipeRefreshLayout swipeRefreshLayout;

    private String BASE_URL;
    private String transportVersion, securityVersion;
    private ApiManager apiManager;
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
        getUserId();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (BuildConfig.FLAVOR_transport.equals("ble") && BLEProvisionLanding.isBleWorkDone) {
            BLEProvisionLanding.bleTransport.disconnect();
        }
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

    private void initViews() {

        btnProvision = findViewById(R.id.btn_provision);
        tvNoDevice = findViewById(R.id.tv_no_device);
        tvTitleDevices = findViewById(R.id.txt_devices);
        recyclerView = findViewById(R.id.rv_device_list);
        swipeRefreshLayout = findViewById(R.id.swipe_container);
        btnProvision.setOnClickListener(provisionBtnClickListener);

        // set a LinearLayoutManager with default orientation
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(linearLayoutManager); // set LayoutManager to RecyclerView
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        deviceAdapter = new EspDeviceAdapter(this, devices);
        recyclerView.setAdapter(deviceAdapter);

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                getDevices();
            }
        });
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

        apiManager.getDevices(new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                closeWaitDialog();
                swipeRefreshLayout.setRefreshing(false);
                devices = data.getParcelableArrayList("devices");
                Log.e(TAG, "Device list size : " + devices.size());

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
        progressDialog.setCancelable(false);
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
}
