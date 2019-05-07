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
package com.espressif.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

import com.espressif.avs.ConfigureAVS;
import com.espressif.provision.Provision;
import com.espressif.provision.R;
import com.espressif.provision.security.Security;
import com.espressif.provision.security.Security0;
import com.espressif.provision.security.Security1;
import com.espressif.provision.session.Session;
import com.espressif.provision.transport.BLETransport;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.provision.transport.SoftAPTransport;
import com.espressif.provision.transport.Transport;
import com.google.protobuf.InvalidProtocolBufferException;

import avs.Avsconfig;

import static com.espressif.avs.ConfigureAVS.AVS_CONFIG_PATH;

public class LoginWithAmazon extends AppCompatActivity {

    private static final String TAG = "Espressif:" + LoginWithAmazon.class.getSimpleName();

    public static BLETransport BLE_TRANSPORT = null;
    public static final String KEY_HOST_ADDRESS = "host_address";
    public static final String KEY_IS_PROVISIONING = "is_provisioning";
    private static final String PROOF_OF_POSSESSION = "abcd1234";

    private Session session;
    private Security security;
    private Transport transport;

    public String[] DeviceDetails = new String[3];
    int galat_hai = 0;
    private String hostAddress;
    private boolean isProvisioning = false;
    private String productId;
    private String productDSN;
    private String codeVerifier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_with_amazon);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        hostAddress = intent.getStringExtra(KEY_HOST_ADDRESS);
        isProvisioning = intent.getBooleanExtra(KEY_IS_PROVISIONING, false);

        View loginButton = findViewById(R.id.login_with_amazon);
        loginButton.setOnClickListener(loginBtnClickListener);

        if (isProvisioning) {

            transport = BLE_TRANSPORT;
            security = new Security1(PROOF_OF_POSSESSION);

        } else {

            Log.d(TAG, "Host Address : " + hostAddress);
            transport = new SoftAPTransport(hostAddress + ":80");
            security = new Security0();
        }

        session = new Session(transport, security);
        session.sessionListener = sessionListener;
        session.init(null);
    }

    Session.SessionListener sessionListener = new Session.SessionListener() {

        @Override
        public void OnSessionEstablished() {

            Log.d(TAG, "Session established");

            getDeviceDetails(new ConfigureAVS.ConfigureAVSActionListener() {

                @Override
                public void onComplete(Avsconfig.AVSConfigStatus status, Exception e) {

                    // This is where we fetch the code verifier from the ESP32 device
                    productId = DeviceDetails[0];
                    productDSN = DeviceDetails[1];
                    codeVerifier = DeviceDetails[2];
                }
            });
        }

        @Override
        public void OnSessionEstablishFailed(Exception e) {

            Log.e(TAG, "Session failed");
            e.printStackTrace();
        }
    };

    private View.OnClickListener loginBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Log.d(TAG, "Login button Clicked");
            ConfigureAVS.loginWithAmazon(LoginWithAmazon.this,
                    productId,
                    productDSN,
                    codeVerifier,
                    new ConfigureAVS.AmazonLoginListener() {

                        @Override
                        public void LoginSucceeded(String clientId, String authCode, String redirectUri, String codeVerifier) {

                            Log.d(TAG, "LoginSucceeded");
                            Log.d(TAG, "clientId : " + clientId);
                            Log.d(TAG, "authCode : " + authCode);
                            Log.d(TAG, "redirectUri : " + redirectUri);
                            Log.d(TAG, "codeVerifier : " + codeVerifier);

                            if (isProvisioning) {

                                ProvisionActivity.BLE_TRANSPORT = BLE_TRANSPORT;

                                Intent launchProvisionInstructions = new Intent(getApplicationContext(), WiFiScanList.class);
                                launchProvisionInstructions.putExtras(getIntent());
                                launchProvisionInstructions.putExtra(ConfigureAVS.CLIENT_ID_KEY, clientId);
                                launchProvisionInstructions.putExtra(ConfigureAVS.AUTH_CODE_KEY, authCode);
                                launchProvisionInstructions.putExtra(ConfigureAVS.REDIRECT_URI_KEY, redirectUri);
                                launchProvisionInstructions.putExtra(ConfigureAVS.CODE_VERIFIER_KEY, codeVerifier);
                                startActivityForResult(launchProvisionInstructions, Provision.REQUEST_PROVISIONING_CODE);

                            } else {

                                // TODO
                            }
                        }

                        @Override
                        public void LoginFailed() {
                            // TODO
                        }
                    });
        }
    };

    public void getDeviceDetails(final ConfigureAVS.ConfigureAVSActionListener configureAVSActionListener) {

        Log.e(TAG, "Get Device Details");
        Avsconfig.CmdGetDetails configRequest = Avsconfig.CmdGetDetails.newBuilder()
                .setDummy(67172)
                .build();
        Avsconfig.AVSConfigMsgType msgType = Avsconfig.AVSConfigMsgType.TypeCmdGetDetails;
        Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.newBuilder()
                .setCmdGetDetails(configRequest)
                .setMsg(msgType)
                .build();
        byte[] message = security.encrypt(payload.toByteArray());

        transport.sendConfigData(AVS_CONFIG_PATH, message, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {
                Log.d(TAG, "Get Device Details - onSuccess");
                DeviceDetails = processSetAVSConfigResponse(returnData, configureAVSActionListener);
            }

            @Override
            public void onFailure(Exception e) {
            }
        });
    }

    private String[] processSetAVSConfigResponse(byte[] responseData, ConfigureAVS.ConfigureAVSActionListener configureAVSActionListener) {

        byte[] decryptedData = security.decrypt(responseData);
        try {
            Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.parseFrom(decryptedData);
            Avsconfig.RespGetDetails response = payload.getRespGetDetails();
            DeviceDetails[0] = response.getProductID();
            DeviceDetails[1] = response.getDSN();
            DeviceDetails[2] = response.getCodeChallenge();
            String deviceVersion = response.getVersion();
            Log.d(TAG, "ProductID : " + DeviceDetails[0] + ", DSN : " + DeviceDetails[1] + ", CodeChallenge : " + DeviceDetails[2]);

            String appVersion = getResources().getString(R.string.avsconfigversion);
            if (appVersion.compareTo(deviceVersion) != 0) {
                View loginButton = findViewById(R.id.login_with_amazon);
                Snackbar snackbar = Snackbar
                        .make(loginButton, "Version mismatch! App- " + appVersion + " Device- " + deviceVersion + ".\nContinuing anyway.", Snackbar.LENGTH_INDEFINITE)
                        .setAction("OK", new OkayListener())
                        .setActionTextColor(Color.WHITE);

                snackbar.show();
            }
            Avsconfig.AVSConfigStatus status = response.getStatus();
            configureAVSActionListener.onComplete(status, null);
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return DeviceDetails;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Provision.REQUEST_PROVISIONING_CODE &&
                resultCode == RESULT_OK) {
            setResult(resultCode);
            finish();
        }
    }

    public class OkayListener implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            // Code to undo the user's last action
        }
    }
}
