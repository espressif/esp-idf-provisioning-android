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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ContentLoadingProgressBar;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.provision.Provision;
import com.espressif.provision.R;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.provision.transport.SoftAPTransport;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class ProvisionLanding extends AppCompatActivity {

    private static final String TAG = "Espressif::" + ProvisionLanding.class.getSimpleName();

    private static final int WIFI_SETTINGS_ACTIVITY_REQUEST = 121;

    private String currentSSID;
    private String deviceNamePrefix;
    private String securityVersion;
    public static SoftAPTransport softAPTransport;
    public static ArrayList<String> deviceCapabilities;
    private SharedPreferences sharedPreferences;

    private TextView tvTitle, tvBack, tvCancel;
    private CardView btnConnect;
    private TextView txtConnectBtn;
    private ImageView arrowImage;
    private ContentLoadingProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provision_landing);

        sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        deviceNamePrefix = sharedPreferences.getString(AppConstants.KEY_WIFI_NETWORK_NAME_PREFIX, "");
        securityVersion = getIntent().getStringExtra(Provision.CONFIG_SECURITY_KEY);
        deviceCapabilities = new ArrayList<>();

        initViews();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case WIFI_SETTINGS_ACTIVITY_REQUEST:
                updateUi();
                break;

            case Provision.REQUEST_PROVISIONING_CODE:
                if (resultCode == RESULT_OK) {
                    setResult(resultCode);
                    finish();
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {

        switch (requestCode) {

            case Provision.REQUEST_PERMISSIONS_CODE: {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                }
            }
            break;
        }
    }

    View.OnClickListener btnConnectClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vib.vibrate(HapticFeedbackConstants.VIRTUAL_KEY);

            deviceNamePrefix = sharedPreferences.getString(AppConstants.KEY_WIFI_NETWORK_NAME_PREFIX, "");
            currentSSID = fetchWifiSSID();

            if (currentSSID != null && currentSSID.startsWith(deviceNamePrefix)) {

                connectDevice();
            } else {
                startActivityForResult(new Intent(Settings.ACTION_WIFI_SETTINGS), WIFI_SETTINGS_ACTIVITY_REQUEST);
            }
        }
    };

    private void connectDevice() {

        String baseUrl = getIntent().getStringExtra(Provision.CONFIG_BASE_URL_KEY);
        softAPTransport = new SoftAPTransport(baseUrl);
        String tempData = "ESP";

        softAPTransport.sendConfigData(AppConstants.HANDLER_PROTO_VER, tempData.getBytes(), new ResponseListener() {

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
                    deviceCapabilities = new ArrayList<>();

                    for (int i = 0; i < capabilities.length(); i++) {
                        String cap = capabilities.getString(i);
                        deviceCapabilities.add(cap);
                    }
                    Log.d(TAG, "Capabilities : " + deviceCapabilities);

                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.d(TAG, "Capabilities JSON not available.");
                }

                if (!deviceCapabilities.contains("no_pop") && securityVersion.equals(Provision.CONFIG_SECURITY_SECURITY1)) {

                    goToPopActivity();

                } else if (deviceCapabilities.contains("wifi_scan")) {

                    goToWifiScanListActivity();

                } else {

                    goToProvisionActivity();
                }
            }

            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
            }
        });
    }

    private View.OnClickListener cancelButtonClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            finish();
        }
    };

    private void initViews() {

        tvTitle = findViewById(R.id.main_toolbar_title);
        tvBack = findViewById(R.id.btn_back);
        tvCancel = findViewById(R.id.btn_cancel);

        tvTitle.setText(R.string.title_activity_connect_device);
        tvBack.setVisibility(View.GONE);
        tvCancel.setVisibility(View.VISIBLE);

        tvCancel.setOnClickListener(cancelButtonClickListener);

        btnConnect = findViewById(R.id.btn_connect);
        txtConnectBtn = findViewById(R.id.text_btn);
        arrowImage = findViewById(R.id.iv_arrow);
        progressBar = findViewById(R.id.progress_indicator);

        txtConnectBtn.setText(R.string.btn_connect);
        btnConnect.setOnClickListener(btnConnectClickListener);
    }

    private void updateUi() {

        ((EspApplication) getApplicationContext()).enableOnlyWifiNetwork();

        currentSSID = fetchWifiSSID();
        Log.d(TAG, "SSID : " + currentSSID);

        if (currentSSID != null && (currentSSID.startsWith(deviceNamePrefix) || currentSSID.equals(deviceNamePrefix))) {

            btnConnect.setEnabled(false);
            btnConnect.setAlpha(0.5f);
            txtConnectBtn.setText(R.string.btn_connecting);
            progressBar.setVisibility(View.VISIBLE);
            arrowImage.setVisibility(View.GONE);
            connectDevice();

        } else {

            btnConnect.setEnabled(true);
            btnConnect.setAlpha(1f);
            txtConnectBtn.setText(R.string.btn_connect);
            progressBar.setVisibility(View.GONE);
            arrowImage.setVisibility(View.VISIBLE);
        }
    }

    private String fetchWifiSSID() {

        ((EspApplication) getApplicationContext()).enableOnlyWifiNetwork();
        String ssid = null;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo == null) {
                return null;
            }

            if (networkInfo.isConnected()) {
                final WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
                if (connectionInfo != null) {
                    ssid = connectionInfo.getSSID();
                    ssid = ssid.replaceAll("^\"|\"$", "");
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, Provision.REQUEST_PERMISSIONS_CODE);
        }
        return ssid;
    }

    private void goToPopActivity() {

        finish();
        Intent popIntent = new Intent(getApplicationContext(), ProofOfPossessionActivity.class);
        popIntent.putExtra(AppConstants.KEY_DEVICE_NAME, currentSSID);
        popIntent.putExtras(getIntent());
        startActivity(popIntent);
    }

    private void goToWifiScanListActivity() {

        finish();
        Intent wifiListIntent = new Intent(getApplicationContext(), WiFiScanActivity.class);
        wifiListIntent.putExtras(getIntent());
        wifiListIntent.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, "");
        startActivity(wifiListIntent);
    }

    private void goToProvisionActivity() {

        finish();
        Intent launchProvisionInstructions = new Intent(getApplicationContext(), ProvisionActivity.class);
        launchProvisionInstructions.putExtras(getIntent());
        launchProvisionInstructions.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, "");
        startActivityForResult(launchProvisionInstructions, Provision.REQUEST_PROVISIONING_CODE);
    }
}
