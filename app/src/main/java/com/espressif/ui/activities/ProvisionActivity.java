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
import com.espressif.avs.ConfigureAVS;
import com.espressif.provision.Provision;
import com.espressif.provision.R;
import com.espressif.provision.security.Security;
import com.espressif.provision.security.Security0;
import com.espressif.provision.security.Security1;
import com.espressif.provision.session.Session;
import com.espressif.provision.transport.BLETransport;
import com.espressif.provision.transport.SoftAPTransport;
import com.espressif.provision.transport.Transport;

import espressif.Constants;
import espressif.WifiConstants;

public class ProvisionActivity extends AppCompatActivity {

    private static final String TAG = "Espressif::" + ProvisionActivity.class.getSimpleName();

    private TextView ssid;
    private EditText ssidInput;
    private EditText passwordInput;
    private Button btnProvision;
    private ProgressBar progressBar;

    private int wifiSecurityType;
    private String ssidValue, passphraseValue;
    private String pop, baseUrl, transportVersion, securityVersion;
    private String deviceNamePrefix, deviceUUID, sessionUUID, configUUID, avsconfigUUID;

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
        Log.e(TAG, "POP : " + pop);
        baseUrl = intent.getStringExtra(Provision.CONFIG_BASE_URL_KEY);
        transportVersion = intent.getStringExtra(Provision.CONFIG_TRANSPORT_KEY);
        securityVersion = intent.getStringExtra(Provision.CONFIG_SECURITY_KEY);
        deviceNamePrefix = intent.getStringExtra(BLETransport.DEVICE_NAME_PREFIX_KEY);

        deviceUUID = intent.getStringExtra(BLETransport.SERVICE_UUID_KEY);
        sessionUUID = intent.getStringExtra(BLETransport.SESSION_UUID_KEY);
        configUUID = intent.getStringExtra(BLETransport.CONFIG_UUID_KEY);
        avsconfigUUID = intent.getStringExtra(ConfigureAVS.AVS_CONFIG_UUID_KEY);

        ssid = findViewById(R.id.ssid_text);
        ssidInput = findViewById(R.id.ssid_input);
        passwordInput = findViewById(R.id.password_input);
        btnProvision = findViewById(R.id.btn_provision);
        progressBar = findViewById(R.id.progress_indicator);

        if (TextUtils.isEmpty(wifiSSID)) {

            ssid.setVisibility(View.GONE);
            ssidInput.setVisibility(View.VISIBLE);
        } else {

            ssidInput.setVisibility(View.GONE);
            ssid.setVisibility(View.VISIBLE);
            ssid.setText(wifiSSID);

            // Security feature is not available in Alexa release. Uncomment below code once firmware sends wifi sec type.
//            if (wifiSecurityType == AppConstants.WIFI_OPEN) {
//
//                passwordInput.setVisibility(View.GONE);
//                findViewById(R.id.password_input_layout).setVisibility(View.GONE);
//                btnProvision.setEnabled(false);
//                btnProvision.setAlpha(0.5f);
//                btnProvision.setTextColor(Color.WHITE);
//                doProvisioning();
//            }
        }

        ssidValue = wifiSSID;
        Log.d("ProvisionActivity", "Selected AP -" + wifiSSID);

//        btnProvision.setEnabled(false);
//        btnProvision.setAlpha(0.5f);
//        btnProvision.setTextColor(Color.WHITE);
        enableProvisionBtn();

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

        final Security security;
        final Transport transport;

        if (securityVersion.equals(Provision.CONFIG_SECURITY_SECURITY1)) {
            security = new Security1(pop);
        } else {
            security = new Security0();
        }

        if (transportVersion.equals(Provision.CONFIG_TRANSPORT_WIFI)) {

            transport = new SoftAPTransport(baseUrl);
            provision(transport, security);

        } else if (transportVersion.equals(Provision.CONFIG_TRANSPORT_BLE)) {

            if (BLEProvisionLanding.bleTransport == null) {

                Log.e(TAG, "BLE Transport is Null. It should not be null.");
                BLEProvisionLanding.isBleWorkDone = true;
                finish();

            } else {
                provision(BLEProvisionLanding.bleTransport, security);
            }
        }
    }

    private void provision(Transport transport, Security security) {

        Log.e(TAG, "================== PROVISION +++++++++++++++++++++++++++++");

        final Session session = new Session(transport, security);
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
                        String statusText = "";
                        if (e != null) {
                            statusText = e.getMessage();
                        } else if (newStatus == WifiConstants.WifiStationState.Connected) {
                            statusText = getResources().getString(R.string.success_text);
                        } else if (newStatus == WifiConstants.WifiStationState.Disconnected) {
                            statusText = getResources().getString(R.string.wifi_disconnected_text);
                        } else {
                            if (failedReason == WifiConstants.WifiConnectFailedReason.AuthError) {
                                statusText = getResources().getString(R.string.error_authentication_failed);
                            } else if (failedReason == WifiConstants.WifiConnectFailedReason.NetworkNotFound) {
                                statusText = getResources().getString(R.string.error_network_not_found);
                            } else {
                                statusText = getResources().getString(R.string.error_unknown);
                            }
                        }
                        final String finalStatusText = statusText;

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {

                                if (newStatus != WifiConstants.WifiStationState.Connected) {
                                    goToSuccessPage(finalStatusText);
                                } else {
                                    if (LoginWithAmazon.isLoginSkipped) {
                                        goToSuccessPage(finalStatusText);
                                    } else {
                                        goToAlexaScreen();
                                    }
                                }
                            }
                        });
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

            @Override
            public void OnSessionEstablishFailed(Exception e) {
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

    private void goToAlexaScreen() {

        toggleFormState(true);
        finish();
        Intent goToAlexaIntent = new Intent(getApplicationContext(), AlexaActivity.class);
        goToAlexaIntent.putExtra("is_prov", true);
        goToAlexaIntent.putExtras(getIntent());
        startActivity(goToAlexaIntent);
    }

    private void goToSuccessPage(String statusText) {

        toggleFormState(true);
        finish();
        Intent goToSuccessPage = new Intent(getApplicationContext(), ProvisionSuccessActivity.class);
        goToSuccessPage.putExtra(AppConstants.KEY_STATUS_MSG, statusText);
        goToSuccessPage.putExtras(getIntent());
        startActivity(goToSuccessPage);
    }
}
