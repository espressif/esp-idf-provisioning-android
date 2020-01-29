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
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.widget.ContentLoadingProgressBar;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.provision.BuildConfig;
import com.espressif.provision.Provision;
import com.espressif.provision.R;
import com.espressif.provision.session.Session;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.provision.transport.Transport;
import com.espressif.ui.models.UpdateEvent;
import com.google.protobuf.InvalidProtocolBufferException;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.UUID;

import cloud.Cloud;
import espressif.Constants;
import espressif.WifiConstants;

public class ProvisionActivity extends AppCompatActivity {

    private static final String TAG = ProvisionActivity.class.getSimpleName();

    private static final long ADD_DEVICE_REQ_TIME = 5000;

    private TextView tvTitle, tvBack, tvCancel;
    private ImageView tick1, tick2, tick3, tick4;
    private ContentLoadingProgressBar progress1, progress2, progress3, progress4;

    private CardView btnOk;
    private TextView txtOkBtn;

    private int addDeviceReqCount = 0;
    private String ssidValue, passphraseValue = "";
    private String pop, baseUrl, transportVersion, securityVersion;
    private String deviceSecret, secretKey;

    private Transport transport;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provision);

        Intent intent = getIntent();
        ssidValue = intent.getStringExtra(Provision.PROVISIONING_WIFI_SSID);
        passphraseValue = intent.getStringExtra(Provision.PROVISIONING_WIFI_PASSWORD);
        pop = intent.getStringExtra(AppConstants.KEY_PROOF_OF_POSSESSION);
        baseUrl = intent.getStringExtra(Provision.CONFIG_BASE_URL_KEY);
        transportVersion = intent.getStringExtra(Provision.CONFIG_TRANSPORT_KEY);
        securityVersion = intent.getStringExtra(Provision.CONFIG_SECURITY_KEY);

        initViews();
        handler = new Handler();

        if (BuildConfig.FLAVOR_transport.equals("ble")) {
            transport = BLEProvisionLanding.bleTransport;
        } else {
            transport = ProvisionLanding.softAPTransport;
        }

        Log.d(TAG, "Selected AP -" + ssidValue);
        Log.e(TAG, "POP : " + pop);
        showLoading();
        doStep1();
    }

    @Override
    public void onBackPressed() {
        BLEProvisionLanding.isBleWorkDone = true;
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UpdateEvent event) {
        Log.e(TAG, "ON UPDATE EVENT RECEIVED : " + event.getEventType());

        switch (event.getEventType()) {

            case EVENT_DEVICE_ADDED:
                goToSuccessPage(getString(R.string.add_device_success_text));
                tick4.setImageResource(R.drawable.ic_checkbox_on);
                tick4.setVisibility(View.VISIBLE);
                progress4.setVisibility(View.GONE);
                break;

            case EVENT_ADD_DEVICE_TIME_OUT:
                tick4.setImageResource(R.drawable.ic_error);
                tick4.setVisibility(View.VISIBLE);
                progress4.setVisibility(View.GONE);
                goToSuccessPage("Add device not confirmed from cloud.");
                break;
        }
    }

    private View.OnClickListener cancelBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            finish();
        }
    };

    private View.OnClickListener okBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            finish();
        }
    };

    private void initViews() {

        tvTitle = findViewById(R.id.main_toolbar_title);
        tvBack = findViewById(R.id.btn_back);
        tvCancel = findViewById(R.id.btn_cancel);

        tick1 = findViewById(R.id.iv_tick_1);
        tick2 = findViewById(R.id.iv_tick_2);
        tick3 = findViewById(R.id.iv_tick_3);
        tick4 = findViewById(R.id.iv_tick_4);

        progress1 = findViewById(R.id.prov_progress_1);
        progress2 = findViewById(R.id.prov_progress_2);
        progress3 = findViewById(R.id.prov_progress_3);
        progress4 = findViewById(R.id.prov_progress_4);

        tvTitle.setText(R.string.title_activity_provisioning);
        tvBack.setVisibility(View.GONE);
        tvCancel.setVisibility(View.VISIBLE);

        tvCancel.setOnClickListener(cancelBtnClickListener);

        btnOk = findViewById(R.id.btn_ok);
        txtOkBtn = findViewById(R.id.text_btn);

        txtOkBtn.setText(R.string.btn_ok);
        btnOk.setOnClickListener(okBtnClickListener);
    }

    private void doStep1() {

        tick1.setVisibility(View.GONE);
        progress1.setVisibility(View.VISIBLE);

        associateDevice();
    }

    private void doStep2() {

        tick1.setImageResource(R.drawable.ic_checkbox_on);
        tick1.setVisibility(View.VISIBLE);
        progress1.setVisibility(View.GONE);
        tick2.setVisibility(View.GONE);
        progress2.setVisibility(View.VISIBLE);
    }

    private void doStep3(boolean isSuccessInStep2) {

        if (isSuccessInStep2) {
            tick2.setImageResource(R.drawable.ic_checkbox_on);
        } else {
            tick2.setImageResource(R.drawable.ic_alert);
        }

        tick2.setVisibility(View.VISIBLE);
        progress2.setVisibility(View.GONE);
        tick3.setVisibility(View.GONE);
        progress3.setVisibility(View.VISIBLE);

        ((EspApplication) getApplicationContext()).disableOnlyWifiNetwork();

        if (BuildConfig.FLAVOR_transport.equals("ble")) {

            addDeviceToCloud(new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
//                            Toast.makeText(ProvisionActivity2.this, "Getting confirmation from cloud", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void onFailure(Exception exception) {

                    tick3.setImageResource(R.drawable.ic_error);
                    tick3.setVisibility(View.VISIBLE);
                    progress3.setVisibility(View.GONE);
                    hideLoading();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ProvisionActivity.this, "Add Device Failed", Toast.LENGTH_LONG).show();
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

    private void doStep4() {

        hideLoading();
        tick3.setImageResource(R.drawable.ic_checkbox_on);
        tick3.setVisibility(View.VISIBLE);
        progress3.setVisibility(View.GONE);
        tick4.setVisibility(View.GONE);
        progress4.setVisibility(View.VISIBLE);
    }

//    private void doProvisioning() {
//
//        tick1.setVisibility(View.GONE);
//        progress1.setVisibility(View.VISIBLE);
//
//        security = WiFiScanActivity.security;
//
//        if (security == null) {
//            if (securityVersion.equals(Provision.CONFIG_SECURITY_SECURITY1)) {
//                security = new Security1(pop);
//            } else {
//                security = new Security0();
//            }
//        }
//
//        if (transportVersion.equals(Provision.CONFIG_TRANSPORT_WIFI)) {
//
//            if (ProvisionLanding.softAPTransport != null) {
//                transport = ProvisionLanding.softAPTransport;
//            } else {
//                transport = new SoftAPTransport(baseUrl);
//            }
//
//            associateDevice();
//
//        } else if (transportVersion.equals(Provision.CONFIG_TRANSPORT_BLE)) {
//
//            if (BLEProvisionLanding.bleTransport == null) {
//
//                Log.e(TAG, "BLE Transport is Null. It should not be null.");
//                BLEProvisionLanding.isBleWorkDone = true;
//                finish();
//
//            } else {
//                transport = BLEProvisionLanding.bleTransport;
//                associateDevice();
//            }
//        }
//    }

    private void provision() {

        Log.d(TAG, "================== PROVISION +++++++++++++++++++++++++++++");
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
//                Toast.makeText(ProvisionActivity2.this, "Sending network credentials", Toast.LENGTH_SHORT).show();
            }
        });

        if (WiFiScanActivity.session == null) {

            Log.e(TAG, "Session is null");

            WiFiScanActivity.session = new Session(transport, WiFiScanActivity.security);
            WiFiScanActivity.session.sessionListener = new Session.SessionListener() {

                @Override
                public void OnSessionEstablished() {

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
//                            Toast.makeText(ProvisionActivity2.this, "Session Established", Toast.LENGTH_SHORT).show();
                        }
                    });

                    applyConfig();
                }

                @Override
                public void OnSessionEstablishFailed(Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ProvisionActivity.this, "Cannot establish session", Toast.LENGTH_LONG).show();
                            toggleFormState(true);
                        }
                    });
                }
            };
            WiFiScanActivity.session.init(null);
        } else {
            Log.e(TAG, "Session is not null");
            applyConfig();
        }
    }

    private void applyConfig() {

        Log.d(TAG, "Session established : " + WiFiScanActivity.session.isEstablished());
        final Provision provision = new Provision(WiFiScanActivity.session);

        provision.provisioningListener = new Provision.ProvisioningListener() {
            @Override
            public void OnApplyConfigurationsSucceeded() {

                Log.e(TAG, "Applying Config success");

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
//                        Toast.makeText(ProvisionActivity2.this, "Configurations successfully applied", Toast.LENGTH_LONG).show();
                        doStep2();
                    }
                });
            }

            @Override
            public void OnApplyConfigurationsFailed() {

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        tick1.setImageResource(R.drawable.ic_error);
                        tick1.setVisibility(View.VISIBLE);
                        progress1.setVisibility(View.GONE);
                        hideLoading();

                        Toast.makeText(ProvisionActivity.this, "Configurations cannot be applied", Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void OnWifiConnectionStatusUpdated(final WifiConstants.WifiStationState newStatus,
                                                      final WifiConstants.WifiConnectFailedReason failedReason,
                                                      final Exception e) {

                Log.d(TAG, "OnWifiConnectionStatusUpdated, newStatus : " + newStatus);
                String statusText = "";

                if (e != null) {

//                    runOnUiThread(new Runnable() {
//
//                        @Override
//                        public void run() {
//                            tick2.setImageResource(R.drawable.ic_alert_triangle);
//                            tick2.setVisibility(View.VISIBLE);
//                            progress2.setVisibility(View.GONE);
//                            hideLoading();
//                        }
//                    });
//
//                    statusText = e.getMessage();
//                    goToSuccessPage(statusText);

                } else if (newStatus == WifiConstants.WifiStationState.Connected) {
//                    statusText = getResources().getString(R.string.success_text);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
//                            Toast.makeText(ProvisionActivity2.this, "Adding device to cloud", Toast.LENGTH_SHORT).show();
                            doStep3(true);
                        }
                    });

                } else if (newStatus == WifiConstants.WifiStationState.Disconnected
                        || newStatus == WifiConstants.WifiStationState.ConnectionFailed) {

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            doStep3(false);
                        }
                    });

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

                        tick2.setImageResource(R.drawable.ic_error);
                        tick2.setVisibility(View.VISIBLE);
                        progress2.setVisibility(View.GONE);

                        Toast.makeText(ProvisionActivity.this, "Provisioning Failed", Toast.LENGTH_LONG).show();
                        toggleFormState(true);
                    }
                });
                setResult(RESULT_CANCELED);
//                finish();
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
//            btnProvision.setEnabled(true);
//            btnProvision.setAlpha(1f);
        } else {
//            btnProvision.setEnabled(false);
//            btnProvision.setAlpha(0.5f);
//            btnProvision.setTextColor(Color.WHITE);
        }
    }

    private void toggleFormState(boolean isEnabled) {

        if (isEnabled) {

//            progressBar.setVisibility(View.GONE);
//            btnProvision.setEnabled(true);
//            btnProvision.setAlpha(1f);
//            ssidInput.setEnabled(true);
//            passwordInput.setEnabled(true);

        } else {

//            progressBar.setVisibility(View.VISIBLE);
//            btnProvision.setEnabled(false);
//            btnProvision.setAlpha(0.5f);
//            btnProvision.setTextColor(Color.WHITE);
//            ssidInput.setEnabled(false);
//            passwordInput.setEnabled(false);
        }
    }

    private void associateDevice() {

        Log.d(TAG, "Associate device");
//        Toast.makeText(ProvisionActivity2.this, "Associating Device", Toast.LENGTH_SHORT).show();

        final String secretKey = UUID.randomUUID().toString();

        Cloud.CmdGetSetDetails deviceSecretRequest = Cloud.CmdGetSetDetails.newBuilder()
                .setUserID(ApiManager.userId)
                .setSecretKey(secretKey)
                .build();
        Cloud.CloudConfigMsgType msgType = Cloud.CloudConfigMsgType.TypeCmdGetSetDetails;
        Cloud.CloudConfigPayload payload = Cloud.CloudConfigPayload.newBuilder()
                .setMsg(msgType)
                .setCmdGetSetDetails(deviceSecretRequest)
                .build();

        byte[] data = WiFiScanActivity.security.encrypt(payload.toByteArray());
        Log.d(TAG, "Send config data");

        transport.sendConfigData(AppConstants.HANDLER_CLOUD_USER_ASSOC, data, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Log.d(TAG, "Successfully sent user id and secrete key");
                processDetails(returnData, secretKey);
            }

            @Override
            public void onFailure(Exception e) {

                Log.e(TAG, "Send config data - onFailure");
                Log.e(TAG, "Error : " + e.getMessage());

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {

                        tick1.setImageResource(R.drawable.ic_error);
                        tick1.setVisibility(View.VISIBLE);
                        progress1.setVisibility(View.GONE);
                        hideLoading();

                        Toast.makeText(ProvisionActivity.this, "Failed to associate device", Toast.LENGTH_SHORT).show();
                    }
                });

                e.printStackTrace();
                String statusText = "Failed to associate device.";
                goToSuccessPage(statusText);
            }
        });
    }

    private void processDetails(byte[] responseData, String secretKey) {

        byte[] decryptedData = WiFiScanActivity.security.decrypt(responseData);

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

                provision();
            }

        } catch (InvalidProtocolBufferException e) {

            e.printStackTrace();
            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    tick1.setImageResource(R.drawable.ic_error);
                    tick1.setVisibility(View.VISIBLE);
                    progress1.setVisibility(View.GONE);
                    hideLoading();
                }
            });
            String statusText = "Failed to associate device.";
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

    private void goToSuccessPage(final String statusText) {

        Log.e(TAG, "goToSuccessPage");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                toggleFormState(true);
//                finish();
//                Intent goToSuccessPage = new Intent(getApplicationContext(), ProvisionSuccessActivity.class);
//                goToSuccessPage.putExtra(AppConstants.KEY_STATUS_MSG, statusText);
//                goToSuccessPage.putExtras(getIntent());
//                startActivity(goToSuccessPage);
            }
        });
    }

    private Runnable addDeviceTask = new Runnable() {

        @Override
        public void run() {

            addDeviceReqCount++;

            addDeviceToCloud(new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
//                            Toast.makeText(ProvisionActivity2.this, "Getting confirmation from cloud", Toast.LENGTH_SHORT).show();
                        }
                    });
                    doStep4();
                }

                @Override
                public void onFailure(Exception exception) {

                    if (addDeviceReqCount == 5) {

                        tick3.setImageResource(R.drawable.ic_error);
                        tick3.setVisibility(View.VISIBLE);
                        progress3.setVisibility(View.GONE);
                        hideLoading();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(ProvisionActivity.this, "Add Device Failed", Toast.LENGTH_LONG).show();
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

    private void showLoading() {

        btnOk.setEnabled(false);
        btnOk.setAlpha(0.5f);
    }

    public void hideLoading() {

        btnOk.setEnabled(true);
        btnOk.setAlpha(1f);
    }
}
