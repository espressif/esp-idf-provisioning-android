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
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.amazon.identity.auth.device.AuthError;
import com.amazon.identity.auth.device.api.authorization.AuthCancellation;
import com.amazon.identity.auth.device.api.authorization.AuthorizationManager;
import com.amazon.identity.auth.device.api.authorization.AuthorizeListener;
import com.amazon.identity.auth.device.api.authorization.AuthorizeRequest;
import com.amazon.identity.auth.device.api.authorization.AuthorizeResult;
import com.amazon.identity.auth.device.api.authorization.ScopeFactory;
import com.amazon.identity.auth.device.api.workflow.RequestContext;
import com.espressif.AppConstants;
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

import org.json.JSONException;
import org.json.JSONObject;

import avs.Avsconfig;

import static com.espressif.avs.ConfigureAVS.AVS_CONFIG_PATH;

public class LoginWithAmazon extends AppCompatActivity {

    private static final String TAG = "Espressif:" + LoginWithAmazon.class.getSimpleName();

    public static final String KEY_HOST_ADDRESS = "host_address";
    public static final String KEY_DEVICE_NAME = "device_name";
    public static final String KEY_IS_PROVISIONING = "is_provisioning";
    public static boolean isLoginSkipped = false;
    private static final String DEVICE_SERIAL_NUMBER_KEY = "deviceSerialNumber";
    private static final String PRODUCT_INSTANCE_ATTRIBUTES_KEY = "productInstanceAttributes";
    private static final String ALEXA_SCOPE = "alexa:all";

    private Session session;
    private Security security;
    private Transport transport;

    public String[] DeviceDetails = new String[3];
    private String hostAddress;
    private String deviceName;
    private boolean isProvisioning = false;
    private String productId;
    private String productDSN;
    private String codeVerifier;

    private TextView txtDeviceName;
    private Button btnLogin;

    private RequestContext requestContext;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        requestContext = RequestContext.create(this);

        setContentView(R.layout.activity_login_with_amazon);
        Intent intent = getIntent();
        hostAddress = intent.getStringExtra(KEY_HOST_ADDRESS);
        deviceName = intent.getStringExtra(KEY_DEVICE_NAME);
        isProvisioning = intent.getBooleanExtra(KEY_IS_PROVISIONING, false);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(deviceName);
        setSupportActionBar(toolbar);

        btnLogin = findViewById(R.id.login_with_amazon);
        txtDeviceName = findViewById(R.id.txt_device_name);
        progressBar = findViewById(R.id.loading_alexa_login);
        isLoginSkipped = false;

        btnLogin.setEnabled(false);
        btnLogin.setAlpha(0.5f);

        if (!TextUtils.isEmpty(deviceName)) {
            txtDeviceName.setText(deviceName);
        }

        if (isProvisioning) {

            transport = BLEProvisionLanding.bleTransport;
            String proofOfPossession = intent.getStringExtra(AppConstants.KEY_PROOF_OF_POSSESSION);
            security = new Security1(proofOfPossession);

        } else {

            Log.d(TAG, "Host Address : " + hostAddress);
            transport = new SoftAPTransport(hostAddress + ":80");
            security = new Security0();
        }

        session = new Session(transport, security);
        session.sessionListener = sessionListener;
        session.init(null);

        requestContext.registerListener(amazonAuthorizeListener);
        btnLogin.setOnClickListener(loginBtnClickListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "Login With Amazon onResume");
        requestContext.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e(TAG, "Login With Amazon onDestroy");
        requestContext.unregisterListener(amazonAuthorizeListener);
    }

    @Override
    public void onBackPressed() {
        BLEProvisionLanding.isBleWorkDone = true;
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.

        if (isProvisioning) {
            getMenuInflater().inflate(R.menu.menu_alexa_sign_in, menu);
            return true;
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_skip) {
            isLoginSkipped = true;
            finish();
            if (isProvisioning) {
                goToWifiScanListActivity();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    Session.SessionListener sessionListener = new Session.SessionListener() {

        @Override
        public void OnSessionEstablished() {

            Log.d(TAG, "Session established");

            getDeviceDetails(new ConfigureAVSActionListener() {

                @Override
                public void onComplete(Avsconfig.AVSConfigStatus status, Exception e) {

                    // This is where we fetch the code verifier from the ESP32 device
                    productId = DeviceDetails[0];
                    productDSN = DeviceDetails[1];
                    codeVerifier = DeviceDetails[2];
                }
            });
            btnLogin.setAlpha(1f);
            btnLogin.setEnabled(true);
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
            Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vib.vibrate(HapticFeedbackConstants.VIRTUAL_KEY);
            progressBar.setVisibility(View.VISIBLE);

            final JSONObject scopeData = new JSONObject();
            final JSONObject productInstanceAttributes = new JSONObject();

            try {
                productInstanceAttributes.put(DEVICE_SERIAL_NUMBER_KEY, productDSN);
                scopeData.put(PRODUCT_INSTANCE_ATTRIBUTES_KEY, productInstanceAttributes);
                scopeData.put("productID", productId);
                String codeChallenge = codeVerifier;
                AuthorizationManager.authorize(new AuthorizeRequest
                        .Builder(requestContext)
                        .addScope(ScopeFactory.scopeNamed(ALEXA_SCOPE, scopeData))
                        .forGrantType(AuthorizeRequest.GrantType.AUTHORIZATION_CODE)
                        .withProofKeyParameters(codeChallenge, "S256")
                        .build());
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
                if (amazonLoginListener != null) {
                    amazonLoginListener.LoginFailed();
                }
            }
        }
    };

    private AuthorizeListener amazonAuthorizeListener = new AuthorizeListener() {

        /* Authorization was completed successfully. */
        @Override
        public void onSuccess(AuthorizeResult result) {

            /* Your app is now authorized for the requested scopes */
            Log.e(TAG, "Client ID is " + result.getClientId());
            Log.e(TAG, "Authorization code is " + result.getAuthorizationCode());
            Log.e(TAG, "Redirect URI is " + result.getRedirectURI());
            if (amazonLoginListener != null) {
                amazonLoginListener.LoginSucceeded(result.getClientId(),
                        result.getAuthorizationCode(),
                        result.getRedirectURI(),
                        codeVerifier);
            }
        }

        /* There was an error during the attempt to authorize the
        application. */
        @Override
        public void onError(AuthError ae) {
            Log.e(TAG, "Amazon Auth error :" + ae.toString());
            if (amazonLoginListener != null) {
                amazonLoginListener.LoginFailed();
            }
        }

        /* Authorization was cancelled before it could be completed. */
        @Override
        public void onCancel(AuthCancellation cancellation) {
            Log.e(TAG, "Amazon Auth error :" + cancellation.getDescription());
            if (amazonLoginListener != null) {
                amazonLoginListener.LoginFailed();
            }
        }
    };

    private AmazonLoginListener amazonLoginListener = new AmazonLoginListener() {

        @Override
        public void LoginSucceeded(String clientId, String authCode, String redirectUri, String codeVerifier) {

            Log.d(TAG, "LoginSucceeded");
            Log.d(TAG, "clientId : " + clientId);
            Log.d(TAG, "authCode : " + authCode);
            Log.d(TAG, "redirectUri : " + redirectUri);
            Log.d(TAG, "codeVerifier : " + codeVerifier);

            Log.d(TAG, "Do Amazon Login");
            if (clientId != null && BuildConfig.FLAVOR_transport.equals("ble")) {

                configureAmazonLogin(clientId,
                        authCode,
                        redirectUri,
                        new ConfigureAVSActionListener() {
                            @Override
                            public void onComplete(Avsconfig.AVSConfigStatus status, Exception e) {

                                Log.d(TAG, "Amazon Login Completed Successfully");

                                runOnUiThread(new Runnable() {

                                    @Override
                                    public void run() {

                                        progressBar.setVisibility(View.GONE);
                                        finish();

                                        if (isProvisioning) {

                                            goToWifiScanListActivity();
                                        } else {
                                            goToAlexaActivity();
                                        }
                                    }
                                });
                            }
                        });
            }
        }

        @Override
        public void LoginFailed() {

            runOnUiThread(new Runnable() {

                @Override
                public void run() {

                    Toast.makeText(LoginWithAmazon.this, "SignIn failed!", Toast.LENGTH_SHORT).show();
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                    progressBar.setVisibility(View.GONE);
                }
            });
        }
    };

    public void getDeviceDetails(final ConfigureAVSActionListener configureAVSActionListener) {

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
                // TODO
            }
        });
    }

    private String[] processSetAVSConfigResponse(byte[] responseData, ConfigureAVSActionListener configureAVSActionListener) {

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

    private void configureAmazonLogin(String clientId,
                                      String authCode,
                                      String redirectUri,
                                      final ConfigureAVSActionListener actionListener) {

        if (this.session.isEstablished()) {

            byte[] message = createSetAVSConfigRequest(clientId,
                    authCode,
                    redirectUri);
            transport.sendConfigData(AVS_CONFIG_PATH, message, new ResponseListener() {
                @Override
                public void onSuccess(byte[] returnData) {
                    Avsconfig.AVSConfigStatus status = processSetAVSConfigResponse(returnData);
                    if (actionListener != null) {
                        actionListener.onComplete(status, null);
                    }
                }

                @Override
                public void onFailure(Exception e) {
                    if (actionListener != null) {
                        actionListener.onComplete(Avsconfig.AVSConfigStatus.InvalidParam, e);
                    }
                }
            });
        }
    }

    private byte[] createSetAVSConfigRequest(String clientId,
                                             String authCode,
                                             String redirectUri) {
        Avsconfig.CmdSetConfig configRequest = Avsconfig.CmdSetConfig.newBuilder()
                .setAuthCode(authCode)
                .setClientID(clientId)
                .setRedirectURI(redirectUri)
                .build();
        Avsconfig.AVSConfigMsgType msgType = Avsconfig.AVSConfigMsgType.TypeCmdSetConfig;
        Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.newBuilder()
                .setMsg(msgType)
                .setCmdSetConfig(configRequest)
                .build();

        return this.security.encrypt(payload.toByteArray());
    }

    private Avsconfig.AVSConfigStatus processSetAVSConfigResponse(byte[] responseData) {
        byte[] decryptedData = this.security.decrypt(responseData);

        Avsconfig.AVSConfigStatus status = Avsconfig.AVSConfigStatus.UNRECOGNIZED;
        try {
            Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.parseFrom(decryptedData);
            Avsconfig.RespSetConfig response = Avsconfig.RespSetConfig.parseFrom(payload.toByteArray());
            status = response.getStatus();
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return status;
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

    private void goToAlexaActivity() {

        Intent alexaIntent = new Intent(getApplicationContext(), AlexaActivity.class);
        alexaIntent.putExtra(LoginWithAmazon.KEY_HOST_ADDRESS, hostAddress);
        alexaIntent.putExtra(LoginWithAmazon.KEY_DEVICE_NAME, deviceName);
        alexaIntent.putExtras(getIntent());
        startActivity(alexaIntent);
    }

    private void goToWifiScanListActivity() {

        Intent wifiListIntent = new Intent(getApplicationContext(), WiFiScanActivity.class);
        wifiListIntent.putExtra(LoginWithAmazon.KEY_DEVICE_NAME, deviceName);
        wifiListIntent.putExtras(getIntent());
        startActivity(wifiListIntent);
    }

    public interface AmazonLoginListener {
        void LoginSucceeded(String clientId, String authCode, String redirectUri, String codeVerifier);

        void LoginFailed();
    }

    public interface ConfigureAVSActionListener {
        void onComplete(Avsconfig.AVSConfigStatus status, Exception e);
    }
}
