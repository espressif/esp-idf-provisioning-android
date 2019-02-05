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

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothClass;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.sip.SipSession;
import android.os.Bundle;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.espressif.avs.ConfigureAVS;
import com.espressif.provision.Provision;
import com.espressif.provision.R;
import com.espressif.provision.security.Security;
import com.espressif.provision.security.Security1;
import com.espressif.provision.session.Session;
import com.espressif.provision.transport.BLETransport;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.provision.transport.Transport;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.EventListener;
import java.util.HashMap;
import java.util.UUID;

import avs.Avsconfig;

import static com.espressif.avs.ConfigureAVS.AVS_CONFIG_PATH;

public class LoginWithAmazon extends AppCompatActivity {
    public static BLETransport BLE_TRANSPORT = null;
    private static final String TAG = "Espressif::" + LoginWithAmazon.class.getSimpleName();
    public Session session;
    final Security security = new Security1("abcd1234");
    public Transport transport;
    public String[] DeviceDetails = new String[3];
    int galat_hai =0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_with_amazon);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Intent intent = getIntent();
        transport = BLE_TRANSPORT;
//        security =
        session = new Session(LoginWithAmazon.BLE_TRANSPORT, security);
        final Activity thisActivity = this;
        final String transportVersion = intent.getStringExtra(Provision.CONFIG_TRANSPORT_KEY);

        session.sessionListener = new Session.SessionListener() {
            @Override
            public void OnSessionEstablished() {
                getDeviceDetails(new ConfigureAVS.ConfigureAVSActionListener(){
                    @Override
                    public void onComplete(Avsconfig.AVSConfigStatus status, Exception e){
                        // This is where we fetch the code verifier from the ESP32 device
                        final String productId = DeviceDetails[0];
                        final String productDSN = DeviceDetails[1];
                        final String codeVerifier = DeviceDetails[2];
                        View loginButton = findViewById(R.id.login_with_amazon);
                        loginButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                ConfigureAVS.loginWithAmazon(thisActivity,
                                        productId,
                                        productDSN,
                                        codeVerifier,
                                        new ConfigureAVS.AmazonLoginListener() {
                                    @Override
                                    public void LoginSucceeded(String clientId, String authCode, String redirectUri, String codeVerifier) {
                                        ProvisionActivity.BLE_TRANSPORT = BLE_TRANSPORT;
                                                        Intent launchProvisionInstructions = new Intent(getApplicationContext(), ProvisionActivity.class);
                                                        launchProvisionInstructions.putExtras(getIntent());
                                                        launchProvisionInstructions.putExtra(ConfigureAVS.CLIENT_ID_KEY, clientId);
                                                        launchProvisionInstructions.putExtra(ConfigureAVS.AUTH_CODE_KEY, authCode);
                                                        launchProvisionInstructions.putExtra(ConfigureAVS.REDIRECT_URI_KEY, redirectUri);
                                                        launchProvisionInstructions.putExtra(ConfigureAVS.CODE_VERIFIER_KEY, codeVerifier);

                                                        startActivityForResult(launchProvisionInstructions, Provision.REQUEST_PROVISIONING_CODE);
                                                    }

                                                    @Override
                                                    public void LoginFailed() {

                                                    }
                                                });
                                    }
                                });
                            }
                        });
                    }



            @Override
            public void OnSessionEstablishFailed(Exception e) {
                Log.d(TAG, "Session failed in LWA");
            }

        };
        session.init(null);
    }

    public void getDeviceDetails(final ConfigureAVS.ConfigureAVSActionListener configureAVSActionListener){
        Avsconfig.CmdGetDetails configRequest = Avsconfig.CmdGetDetails.newBuilder()
                .setDummy(67172)
                .build();
        Avsconfig.AVSConfigMsgType msgType = Avsconfig.AVSConfigMsgType.TypeCmdGetDetails;
        Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.newBuilder()
                .setCmdGetDetails(configRequest)
                .setMsg(msgType)
                .build();
        byte[] message = security.encrypt(payload.toByteArray());

        transport.sendConfigData(AVS_CONFIG_PATH, message, new ResponseListener(){
            @Override
            public void onSuccess(byte[] returnData) {
                DeviceDetails = processSetAVSConfigResponse(returnData, configureAVSActionListener);

            }
            @Override
            public void onFailure(Exception e) {
            }

        });
    };

    private String[] processSetAVSConfigResponse(byte[] responseData, ConfigureAVS.ConfigureAVSActionListener configureAVSActionListener) {
        byte[] decryptedData = security.decrypt(responseData);
        try {
            Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.parseFrom(decryptedData);
            Avsconfig.RespGetDetails response = payload.getRespGetDetails();
            DeviceDetails[0] = response.getProductID();
            DeviceDetails[1] = response.getDSN();
            DeviceDetails[2] = response.getCodeChallenge();
            String deviceVersion = response.getVersion();

            String appVersion = getResources().getString(R.string.avsconfigversion);
            if(appVersion.compareTo(deviceVersion)!= 0) {
                View loginButton = findViewById(R.id.login_with_amazon);
                Snackbar snackbar = Snackbar
                        .make(loginButton, "Version mismatch! App- "+appVersion+" Device- "+deviceVersion+".\nContinuing anyway.", Snackbar.LENGTH_INDEFINITE)
                        .setAction("OK",new OkayListener())
                        .setActionTextColor(Color.WHITE)
                        ;

                snackbar.show();
            }
            Avsconfig.AVSConfigStatus status = response.getStatus();
            configureAVSActionListener.onComplete(status,null);
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
    public class OkayListener implements View.OnClickListener{

        @Override
        public void onClick(View v) {
            // Code to undo the user's last action
        }
    }
}
