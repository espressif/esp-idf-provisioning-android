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

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.espressif.AppConstants;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.provision.BuildConfig;
import com.espressif.provision.Provision;
import com.espressif.provision.R;
import com.espressif.provision.security.Security;
import com.espressif.provision.security.Security0;
import com.espressif.provision.security.Security1;
import com.espressif.provision.session.Session;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.provision.transport.SoftAPTransport;
import com.espressif.provision.transport.Transport;
import com.google.protobuf.InvalidProtocolBufferException;

import cloud.Cloud;
import espressif.Constants;
import espressif.WifiConstants;

public class ProvisionActivity extends AppCompatActivity {

    private static final String TAG = "Espressif::" + ProvisionActivity.class.getSimpleName();

    private static final long ADD_DEVICE_REQ_TIME = 5000;

    private TextView ssid;
    private EditText ssidInput;
    private EditText passwordInput;
    private Button btnProvision;
    private ProgressBar progressBar;

    private int wifiSecurityType, addDeviceReqCount = 0;
    private String ssidValue, passphraseValue = "";
    private String pop, baseUrl, transportVersion, securityVersion;
    private String deviceSecret, secretKey;

    private Session session;
    private Security security;
    private Transport transport;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provision);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_activity_provision);
        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        final String wifiSSID = intent.getStringExtra(Provision.PROVISIONING_WIFI_SSID);
        wifiSecurityType = intent.getIntExtra(AppConstants.KEY_WIFI_SECURITY_TYPE, AppConstants.WIFI_OPEN);
        pop = intent.getStringExtra(AppConstants.KEY_PROOF_OF_POSSESSION);
        baseUrl = intent.getStringExtra(Provision.CONFIG_BASE_URL_KEY);
        transportVersion = intent.getStringExtra(Provision.CONFIG_TRANSPORT_KEY);
        securityVersion = intent.getStringExtra(Provision.CONFIG_SECURITY_KEY);

        ssid = findViewById(R.id.ssid_text);
        ssidInput = findViewById(R.id.ssid_input);
        passwordInput = findViewById(R.id.password_input);
        btnProvision = findViewById(R.id.btn_provision);
        progressBar = findViewById(R.id.progress_indicator);
        handler = new Handler();

        ssidValue = wifiSSID;
        Log.d(TAG, "Selected AP -" + ssidValue);
        Log.e(TAG, "POP : " + pop);

        if (TextUtils.isEmpty(wifiSSID)) {

            ssid.setVisibility(View.GONE);
            ssidInput.setVisibility(View.VISIBLE);

        } else {

            ssidInput.setVisibility(View.GONE);
            ssid.setVisibility(View.VISIBLE);
            ssid.setText(wifiSSID);

            if (wifiSecurityType == AppConstants.WIFI_OPEN) {

                passwordInput.setVisibility(View.GONE);
                findViewById(R.id.password_input_layout).setVisibility(View.GONE);
                btnProvision.setEnabled(false);
                btnProvision.setAlpha(0.5f);
                btnProvision.setTextColor(Color.WHITE);
                doProvisioning();
            }
        }

        btnProvision.setEnabled(false);
        btnProvision.setAlpha(0.5f);
        btnProvision.setTextColor(Color.WHITE);

        ssidInput.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                ssidValue = editable.toString().trim();
                enableProvisionBtn();
            }
        });

        passwordInput.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                passphraseValue = editable.toString().trim();
                enableProvisionBtn();
            }
        });

        btnProvision.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                vib.vibrate(HapticFeedbackConstants.VIRTUAL_KEY);
                doProvisioning();
            }
        });
    }

    @Override
    public void onBackPressed() {
        BLEProvisionLanding.isBleWorkDone = true;
        super.onBackPressed();
    }

    private void doProvisioning() {

        btnProvision.setEnabled(false);
        btnProvision.setAlpha(0.5f);
        btnProvision.setTextColor(Color.WHITE);
        ssidInput.setEnabled(false);
        passwordInput.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);

        security = WiFiScanActivity.security;

        if (security == null) {
            if (securityVersion.equals(Provision.CONFIG_SECURITY_SECURITY1)) {
                security = new Security1(pop);
            } else {
                security = new Security0();
            }
        }

        if (transportVersion.equals(Provision.CONFIG_TRANSPORT_WIFI)) {

            transport = new SoftAPTransport(baseUrl);
            provision();

        } else if (transportVersion.equals(Provision.CONFIG_TRANSPORT_BLE)) {

            if (BLEProvisionLanding.bleTransport == null) {

                Log.e(TAG, "BLE Transport is Null. It should not be null.");
                BLEProvisionLanding.isBleWorkDone = true;
                finish();

            } else {
                transport = BLEProvisionLanding.bleTransport;
                provision();
            }
        }
    }

    private void provision() {

        Log.d(TAG, "================== PROVISION +++++++++++++++++++++++++++++");
        session = WiFiScanActivity.session;

        if (session == null) {

            session = new Session(transport, security);
            final Session finalSession = session;
            session.sessionListener = new Session.SessionListener() {

                @Override
                public void OnSessionEstablished() {

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ProvisionActivity.this,
                                    "Session Established",
                                    Toast.LENGTH_SHORT)
                                    .show();
                        }
                    });

                    applyConfig(finalSession);
                }

                @Override
                public void OnSessionEstablishFailed(Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ProvisionActivity.this,
                                    "Cannot establish session",
                                    Toast.LENGTH_LONG)
                                    .show();
                            toggleFormState(true);
                        }
                    });
                }
            };
            session.init(null);
        } else {
            applyConfig(session);
        }
    }

    private void applyConfig(Session session) {

        Log.d(TAG, "Session established : " + session.isEstablished());
        final Provision provision = new Provision(session);

        provision.provisioningListener = new Provision.ProvisioningListener() {
            @Override
            public void OnApplyConfigurationsSucceeded() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ProvisionActivity.this,
                                "Configurations successfully applied",
                                Toast.LENGTH_LONG)
                                .show();
                    }
                });
            }

            @Override
            public void OnApplyConfigurationsFailed() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ProvisionActivity.this,
                                "Configurations cannot be applied",
                                Toast.LENGTH_LONG)
                                .show();
                    }
                });
            }

            @Override
            public void OnWifiConnectionStatusUpdated(final WifiConstants.WifiStationState newStatus,
                                                      final WifiConstants.WifiConnectFailedReason failedReason,
                                                      final Exception e) {

                Log.d(TAG, "OnWifiConnectionStatusUpdated");
                String statusText = "";
                if (e != null) {
                    statusText = e.getMessage();
                    goToSuccessPage(statusText);
                } else if (newStatus == WifiConstants.WifiStationState.Connected) {
//                    statusText = getResources().getString(R.string.success_text);
                    associateDevice();
                } else if (newStatus == WifiConstants.WifiStationState.Disconnected) {
                    statusText = getResources().getString(R.string.wifi_disconnected_text);
                    goToSuccessPage(statusText);
                } else {

                    if (failedReason == WifiConstants.WifiConnectFailedReason.AuthError) {
                        statusText = getResources().getString(R.string.error_authentication_failed);
                    } else if (failedReason == WifiConstants.WifiConnectFailedReason.NetworkNotFound) {
                        statusText = getResources().getString(R.string.error_network_not_found);
                    } else {
                        statusText = getResources().getString(R.string.error_unknown);
                    }
                    goToSuccessPage(statusText);
                }
            }

            @Override
            public void OnProvisioningFailed(Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ProvisionActivity.this,
                                "Provisioning Failed",
                                Toast.LENGTH_LONG)
                                .show();
                        toggleFormState(true);
                    }
                });
                setResult(RESULT_CANCELED);
                finish();
            }
        };

        provision.configureWifi(ssidValue, passphraseValue, new Provision.ProvisionActionListener() {

            @Override
            public void onComplete(Constants.Status status, Exception e) {

                provision.applyConfigurations(null);
            }
        });
    }

    private void enableProvisionBtn() {

        if (!TextUtils.isEmpty(ssidValue)/* && !TextUtils.isEmpty(passphraseValue) */) {
            btnProvision.setEnabled(true);
            btnProvision.setAlpha(1f);
        } else {
            btnProvision.setEnabled(false);
            btnProvision.setAlpha(0.5f);
            btnProvision.setTextColor(Color.WHITE);
        }
    }

    private void toggleFormState(boolean isEnabled) {

        if (isEnabled) {

            progressBar.setVisibility(View.GONE);
            btnProvision.setEnabled(true);
            btnProvision.setAlpha(1f);
            ssidInput.setEnabled(true);
            passwordInput.setEnabled(true);

        } else {

            progressBar.setVisibility(View.VISIBLE);
            btnProvision.setEnabled(false);
            btnProvision.setAlpha(0.5f);
            btnProvision.setTextColor(Color.WHITE);
            ssidInput.setEnabled(false);
            passwordInput.setEnabled(false);
        }
    }

    private void associateDevice() {

        Log.d(TAG, "Associate device");

        final String secretKey = "espressif"; // TODO generate key

        Cloud.CmdGetSetDetails deviceSecretRequest = Cloud.CmdGetSetDetails.newBuilder()
                .setUserID(ApiManager.userId)
                .setSecretKey(secretKey)
                .build();
        Cloud.CloudConfigMsgType msgType = Cloud.CloudConfigMsgType.TypeCmdGetSetDetails;
        Cloud.CloudConfigPayload payload = Cloud.CloudConfigPayload.newBuilder()
                .setMsg(msgType)
                .setCmdGetSetDetails(deviceSecretRequest)
                .build();

        byte[] data = security.encrypt(payload.toByteArray());
        Log.d(TAG, "Send config data");

        transport.sendConfigData(AppConstants.HANDLER_CLOUD_USER_ASSOC, data, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Log.d(TAG, "Successfully sent user id and secrete key");
                processDetails(returnData, secretKey);
            }

            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
                String statusText = "Add Device Failed";
                goToSuccessPage(statusText);
            }
        });
    }

    private void processDetails(byte[] responseData, String secretKey) {

        byte[] decryptedData = this.security.decrypt(responseData);

        try {
            Cloud.CloudConfigPayload payload = Cloud.CloudConfigPayload.parseFrom(decryptedData);
            Cloud.RespGetSetDetails response = payload.getRespGetSetDetails();

            Log.e(TAG, "Response : " + decryptedData);
            Log.e(TAG, "Response : " + new String(decryptedData));
            Log.e(TAG, "Status : " + response.getStatus());
            Log.e(TAG, "Device Secret : " + response.getDeviceSecret());

            if (response.getStatus() == Cloud.CloudConfigStatus.Success) {

                deviceSecret = response.getDeviceSecret();
                this.secretKey = secretKey;

                if (BuildConfig.FLAVOR_transport.equals("ble")) {

                    addDeviceToCloud(new ApiResponseListener() {

                        @Override
                        public void onSuccess(Bundle data) {
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            goToSuccessPage(getString(R.string.success_text));
                        }

                        @Override
                        public void onFailure(Exception exception) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(ProvisionActivity.this,
                                            "Add Device Failed",
                                            Toast.LENGTH_LONG)
                                            .show();
                                }
                            });
                            String statusText = "Add Device Failed";
                            goToSuccessPage(statusText);
                        }
                    });
                } else {

                    handler.postDelayed(addDeviceTask, ADD_DEVICE_REQ_TIME);
                }
            }

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(ProvisionActivity.this,
                            "Add Device Failed",
                            Toast.LENGTH_LONG)
                            .show();
                }
            });
            String statusText = "Add Device Failed";
            goToSuccessPage(statusText);
        }
    }

    private void addDeviceToCloud(final ApiResponseListener responseListener) {

        ApiManager apiManager = new ApiManager(getApplicationContext());
        apiManager.addDevice(deviceSecret, secretKey, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                responseListener.onSuccess(null);
            }

            @Override
            public void onFailure(Exception exception) {
                exception.printStackTrace();
                responseListener.onFailure(exception);
            }
        });
    }

    private void goToSuccessPage(String statusText) {

        toggleFormState(true);
        finish();
        Intent goToSuccessPage = new Intent(getApplicationContext(), ProvisionSuccessActivity.class);
        goToSuccessPage.putExtra(AppConstants.KEY_STATUS_MSG, statusText);
        goToSuccessPage.putExtras(getIntent());
        startActivity(goToSuccessPage);
    }

    private Runnable addDeviceTask = new Runnable() {

        @Override
        public void run() {

            addDeviceReqCount++;

            addDeviceToCloud(new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    goToSuccessPage(getString(R.string.success_text));
                }

                @Override
                public void onFailure(Exception exception) {

                    if (addDeviceReqCount == 3) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ProvisionActivity.this,
                                        "Add Device Failed",
                                        Toast.LENGTH_LONG)
                                        .show();
                            }
                        });
                        String statusText = "Add Device Failed";
                        goToSuccessPage(statusText);
                    } else {
                        handler.postDelayed(addDeviceTask, ADD_DEVICE_REQ_TIME);
                    }
                }
            });
        }
    };
}
