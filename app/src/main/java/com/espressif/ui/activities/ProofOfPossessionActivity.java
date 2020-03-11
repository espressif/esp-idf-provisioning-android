package com.espressif.ui.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.ContentLoadingProgressBar;

import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.espressif.AppConstants;
import com.espressif.provision.Provision;
import com.espressif.provision.R;
import com.espressif.provision.security.Security;
import com.espressif.provision.security.Security0;
import com.espressif.provision.security.Security1;
import com.espressif.provision.session.Session;
import com.espressif.provision.transport.ResponseListener;
import com.google.protobuf.InvalidProtocolBufferException;

import avs.Avsconfig;

import static com.espressif.avs.ConfigureAVS.AVS_CONFIG_PATH;

public class ProofOfPossessionActivity extends AppCompatActivity {

    private static final String TAG = "Espressif::" + ProofOfPossessionActivity.class.getSimpleName();

    private Button btnNext;
    private TextView textDeviceName;
    private EditText etDeviceKey;
    private ContentLoadingProgressBar progressBar;

    private String securityVersion, transportVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pop);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_activity_pop);
        setSupportActionBar(toolbar);

        btnNext = findViewById(R.id.btn_next);
        textDeviceName = findViewById(R.id.device_name);
        etDeviceKey = findViewById(R.id.et_pop);
        progressBar = findViewById(R.id.progress_indicator);

        String deviceName = getIntent().getStringExtra(AppConstants.KEY_DEVICE_NAME);
        transportVersion = getIntent().getStringExtra(Provision.CONFIG_TRANSPORT_KEY);
        securityVersion = getIntent().getStringExtra(Provision.CONFIG_SECURITY_KEY);

        progressBar.setVisibility(View.INVISIBLE);
        textDeviceName.setText(deviceName);
        btnNext.setOnClickListener(nextBtnClickListener);
        String key = getString(R.string.proof_of_possesion);

        if (!TextUtils.isEmpty(key)) {

            etDeviceKey.setText(key);
            etDeviceKey.setSelection(etDeviceKey.getText().length());
        } else {
            btnNext.setEnabled(false);
            btnNext.setAlpha(0.5f);
        }

        etDeviceKey.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {

                String pop = s.toString();

                if (TextUtils.isEmpty(pop)) {

                    btnNext.setEnabled(false);
                    btnNext.setAlpha(0.5f);

                } else {

                    btnNext.setEnabled(true);
                    btnNext.setAlpha(1f);
                }
            }
        });
    }

    @Override
    public void onBackPressed() {
        BLEProvisionLanding.isBleWorkDone = true;
        super.onBackPressed();
    }

    private View.OnClickListener nextBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            btnNext.setEnabled(false);
            btnNext.setAlpha(0.5f);
            progressBar.setVisibility(View.VISIBLE);

            final String pop = etDeviceKey.getText().toString();
            Log.d(TAG, "POP : " + pop);
            boolean shouldCreateSession = false;

            if (transportVersion.equals(Provision.CONFIG_TRANSPORT_BLE)) {

                if (BLEProvisionLanding.security == null) {

                    if (securityVersion.equals(Provision.CONFIG_SECURITY_SECURITY1)) {
                        BLEProvisionLanding.security = new Security1(pop);
                    } else {
                        BLEProvisionLanding.security = new Security0();
                    }

                    shouldCreateSession = true;

                } else {

                    if (BLEProvisionLanding.session != null) {
                        getStatus();
                    } else {
                        shouldCreateSession = true;
                    }
                }

            } else {

                if (securityVersion.equals(Provision.CONFIG_SECURITY_SECURITY1)) {
                    Security security = new Security1(pop);
                } else {
                    Security security = new Security0();
                }
                // TODO Create session for SoftAP Transport
                Log.e(TAG, "Currently BLE transport is supported in AVS app.");
            }

            if (shouldCreateSession) {

                BLEProvisionLanding.session = new Session(BLEProvisionLanding.bleTransport, BLEProvisionLanding.security);
                BLEProvisionLanding.session.sessionListener = new Session.SessionListener() {

                    @Override
                    public void OnSessionEstablished() {
                        Log.d(TAG, "Session established");
                        getStatus();
                    }

                    @Override
                    public void OnSessionEstablishFailed(Exception e) {
                        Log.d(TAG, "Session failed");
                    }
                };
                BLEProvisionLanding.session.init(null);
            }
        }
    };

    private void getStatus() {

        Avsconfig.CmdSignInStatus configRequest = Avsconfig.CmdSignInStatus.newBuilder()
                .setDummy(123)
                .build();
        Avsconfig.AVSConfigMsgType msgType = Avsconfig.AVSConfigMsgType.TypeCmdSignInStatus;
        Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.newBuilder()
                .setMsg(msgType)
                .setCmdSigninStatus(configRequest)
                .build();

        byte[] message = BLEProvisionLanding.security.encrypt(payload.toByteArray());

        BLEProvisionLanding.bleTransport.sendConfigData(AVS_CONFIG_PATH, message, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Avsconfig.AVSConfigStatus deviceStatus = processSignInStatusResponse(returnData);
                Log.d(TAG, "SignIn Status Received : " + deviceStatus);
                finish();

                if (deviceStatus.equals(Avsconfig.AVSConfigStatus.SignedIn)) {

                    goToProvisionActivity();

                } else {
                    goToLoginActivity();
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.d(TAG, "Error in getting status");
                e.printStackTrace();
            }
        });
    }

    private Avsconfig.AVSConfigStatus processSignInStatusResponse(byte[] responseData) {

        Avsconfig.AVSConfigStatus status = Avsconfig.AVSConfigStatus.InvalidState;
        byte[] decryptedData = BLEProvisionLanding.security.decrypt(responseData);

        try {

            Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.parseFrom(decryptedData);
            Avsconfig.RespSignInStatus signInStatus = payload.getRespSigninStatus();
            status = signInStatus.getStatus();
            Log.d(TAG, "SignIn Status message " + status.getNumber() + status.toString());

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return status;
    }

    private void goToLoginActivity() {

        Intent alexaProvisioningIntent = new Intent(getApplicationContext(), LoginWithAmazon.class);
        alexaProvisioningIntent.putExtras(getIntent());
        alexaProvisioningIntent.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, etDeviceKey.getText().toString());
        startActivity(alexaProvisioningIntent);
    }

    private void goToProvisionActivity() {

        Intent launchProvisionInstructions = new Intent(getApplicationContext(), ProvisionActivity.class);
        launchProvisionInstructions.putExtras(getIntent());
        launchProvisionInstructions.putExtra(AppConstants.KEY_PROOF_OF_POSSESSION, etDeviceKey.getText().toString());
        startActivityForResult(launchProvisionInstructions, Provision.REQUEST_PROVISIONING_CODE);
    }
}
