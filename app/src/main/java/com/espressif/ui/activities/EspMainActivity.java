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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.provision.BuildConfig;
import com.espressif.provision.Provision;
import com.espressif.provision.R;
import com.espressif.ui.adapters.EspDeviceAdapter;
import com.espressif.ui.models.EspDevice;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.UpdateEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class EspMainActivity extends AppCompatActivity {

    private static final String TAG = "Espressif::" + EspMainActivity.class.getSimpleName();

    private static final int REQUEST_LOCATION = 1;
    private static final int QR_CODE_READER_ACTIVITY_REQUEST = 120;

    private ContentLoadingProgressBar progressBar;
    private RecyclerView recyclerView;
    private TextView tvNoDevice, tvAddDevice;
    private RelativeLayout rlNoDevices;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvTitle;
    private ImageView ivAddDevice, ivUserProfile, ivNoDevice;

    private CardView btnAddDevice;
    private TextView txtAddDeviceBtn;
    private ImageView arrowImage;

    private String BASE_URL;
    private String transportVersion, securityVersion;
    private ApiManager apiManager;
    private EspApplication espApp;
    private EspDeviceAdapter deviceAdapter;
    private ArrayList<EspDevice> devices;
    private boolean isFirstTimeSetupDone, isScanQrCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esp_main);

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
        rlNoDevices.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);

        showLoading();
        apiManager = ApiManager.getInstance(getApplicationContext());
        getSupportedVersions();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.d(TAG, "On Resume, isFirstTimeSetupDone : " + isFirstTimeSetupDone + " , isScanQrCode : " + isScanQrCode);

        if (BuildConfig.FLAVOR_transport.equals("ble") && BLEProvisionLanding.isBleWorkDone) {
            BLEProvisionLanding.bleTransport.disconnect();
        }
        EventBus.getDefault().register(this);

        if (apiManager.isTokenExpired()) {

            apiManager.getNewToken();

            new Handler().postDelayed(new Runnable() {

                @Override
                public void run() {
                    getNodes();
                }
            }, 1500);

        } else if (isFirstTimeSetupDone && !isScanQrCode) {
            getNodes();
        }
        isScanQrCode = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

//    @Override
//    protected void onStop() {
//        apiManager.cancelGetNodeStatusTask();
//        super.onStop();
//    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UpdateEvent event) {

        Log.e(TAG, "Update Event Received : " + event.getEventType());

        switch (event.getEventType()) {

            case EVENT_DEVICE_ADDED:
            case EVENT_DEVICE_REMOVED:
                showLoading();
                getNodes();
                break;

            case EVENT_DEVICE_STATUS_UPDATE:
                if (isFirstTimeSetupDone) {
                    updateUi();
                }
                break;
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
            startActivity(new Intent(this, UserProfileActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.d(TAG, "onActivityResult, resultCode : " + resultCode + ", requestCode : " + requestCode);

        switch (requestCode) {

            case REQUEST_LOCATION:

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
                break;


            case QR_CODE_READER_ACTIVITY_REQUEST:

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

                    if (!isLocationEnabled()) {
                        askForLocation();
                        return;
                    }
                }

                if (resultCode != RESULT_OK) {

                    isScanQrCode = false;

                    if (data != null) {
                        String errMsg = data.getStringExtra("err_msg");

                        if (!TextUtils.isEmpty(errMsg)) {
                            Toast.makeText(this, errMsg, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Error in  scanning", Toast.LENGTH_SHORT).show();
                        }
                    }
                    return;
                }

                if (data != null) {

                    Log.i(TAG, "Data is not null");
                    final String deviceName = data.getStringExtra("deviceName");
                    final String pop = data.getStringExtra("pop");

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
                break;
        }
    }

    View.OnClickListener addDeviceBtnClickListener = new View.OnClickListener() {

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
            goToBarCodeActivity();
        }
    };

    private void initViews() {

        tvTitle = findViewById(R.id.esp_toolbar_title);
        ivAddDevice = findViewById(R.id.btn_add_device);
        ivUserProfile = findViewById(R.id.btn_user_profile);
        rlNoDevices = findViewById(R.id.rl_no_device);
        tvNoDevice = findViewById(R.id.tv_no_device);
        tvAddDevice = findViewById(R.id.tv_add_device);
        ivNoDevice = findViewById(R.id.iv_no_device);
        progressBar = findViewById(R.id.progress_get_devices);
        recyclerView = findViewById(R.id.rv_device_list);
        swipeRefreshLayout = findViewById(R.id.swipe_container);

        btnAddDevice = findViewById(R.id.btn_add_device_1);
        txtAddDeviceBtn = findViewById(R.id.text_btn);
        arrowImage = findViewById(R.id.iv_arrow);
        txtAddDeviceBtn.setText(R.string.btn_add_device);
        btnAddDevice.setVisibility(View.GONE);
        arrowImage.setVisibility(View.GONE);

        tvTitle.setText(R.string.title_activity_devices);
        ivAddDevice.setOnClickListener(addDeviceBtnClickListener);
        btnAddDevice.setOnClickListener(addDeviceBtnClickListener);

        ivUserProfile.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startActivity(new Intent(EspMainActivity.this, UserProfileActivity.class));
            }
        });

        // set a LinearLayoutManager with default orientation
        GridLayoutManager linearLayoutManager = new GridLayoutManager(getApplicationContext(), 2);
        recyclerView.setLayoutManager(linearLayoutManager); // set LayoutManager to RecyclerView

        deviceAdapter = new EspDeviceAdapter(this, devices);
        recyclerView.setAdapter(deviceAdapter);

        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {

            @Override
            public void onRefresh() {

                if (apiManager.isTokenExpired()) {

                    apiManager.getNewToken();
                    new Handler().postDelayed(new Runnable() {

                        @Override
                        public void run() {
                            getNodes();
                        }
                    }, 1500);

                } else {
                    getNodes();
                }
            }
        });
    }

    private void getSupportedVersions() {

        apiManager.getSupportedVersions(new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                String updateMsg = data.getString("additional_info");
                ArrayList<String> versions = data.getStringArrayList("supported_versions");

                if (!versions.contains(AppConstants.CURRENT_VERSION)) {
                    alertForForceUpdate(updateMsg);
                } else {

                    int currentVersion = getVersionNumber(AppConstants.CURRENT_VERSION);

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

                getNodes();
            }

            @Override
            public void onFailure(Exception exception) {
                hideLoading();
                isFirstTimeSetupDone = true;
                tvNoDevice.setText("Error : Device list not received");
                rlNoDevices.setVisibility(View.VISIBLE);
                tvNoDevice.setVisibility(View.VISIBLE);
                tvAddDevice.setVisibility(View.GONE);
                ivNoDevice.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            }
        });
    }

    private int getVersionNumber(String versionString) {

        versionString = versionString.replace("v", "");
        int version = Integer.valueOf(versionString);
        return version;
    }

    private void getNodes() {

        Log.e(TAG, "Get Nodes");
        apiManager.getNodes(new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                Log.e(TAG, "Get devices Success received");
                updateUi();
            }

            @Override
            public void onFailure(Exception exception) {

                exception.printStackTrace();
                hideLoading();
                isFirstTimeSetupDone = true;
                swipeRefreshLayout.setRefreshing(false);
                tvNoDevice.setText("Error : Device list not received");
                rlNoDevices.setVisibility(View.VISIBLE);
                tvNoDevice.setVisibility(View.VISIBLE);
                tvAddDevice.setVisibility(View.GONE);
                ivNoDevice.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            }
        });
    }

    private void updateUi() {

        devices.clear();

        for (Map.Entry<String, EspNode> entry : espApp.nodeMap.entrySet()) {

            String key = entry.getKey();
            EspNode node = entry.getValue();

            if (node != null) {
                ArrayList<EspDevice> espDevices = node.getDevices();
                devices.addAll(espDevices);
            }
        }

        Log.d(TAG, "Device list size : " + devices.size());

        if (devices.size() > 0) {

            rlNoDevices.setVisibility(View.GONE);
            btnAddDevice.setVisibility(View.GONE);
            ivAddDevice.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.VISIBLE);

        } else {
            tvNoDevice.setText(R.string.no_devices);
            rlNoDevices.setVisibility(View.VISIBLE);
            tvNoDevice.setVisibility(View.VISIBLE);
            tvAddDevice.setVisibility(View.GONE);
            ivNoDevice.setVisibility(View.VISIBLE);
            btnAddDevice.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            ivAddDevice.setVisibility(View.GONE);
        }

        deviceAdapter.updateList(devices);
        swipeRefreshLayout.setRefreshing(false);
        isFirstTimeSetupDone = true;
        hideLoading();
    }

    private void goToBarCodeActivity() {

        ((EspApplication) getApplicationContext()).enableOnlyWifiNetwork(this);
        isScanQrCode = true;
        Intent intent = new Intent(this, AddDeviceActivity.class);
        startActivityForResult(intent, QR_CODE_READER_ACTIVITY_REQUEST);
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

    private void showLoading() {

        progressBar.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        progressBar.setVisibility(View.GONE);
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
