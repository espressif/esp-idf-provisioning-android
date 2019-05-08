package com.espressif.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.espressif.provision.R;
import com.espressif.provision.security.Security;
import com.espressif.provision.security.Security0;
import com.espressif.provision.session.Session;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.provision.transport.SoftAPTransport;
import com.espressif.provision.transport.Transport;
import com.google.protobuf.InvalidProtocolBufferException;

import avs.Avsconfig;

import static com.espressif.avs.ConfigureAVS.AVS_CONFIG_PATH;

public class AlexaActivity extends AppCompatActivity {

    private static final String TAG = "Espressif:" + AlexaActivity.class.getSimpleName();

    private Button btnSignOut;
//    private AvsEmberLightText txtAlexaAppLink;

    private Session session;
    private Security security;
    private Transport transport;

    private String hostAddress;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alexa);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        hostAddress = intent.getStringExtra(LoginWithAmazon.KEY_HOST_ADDRESS);

        initViews();
    }

    private void initViews() {

        btnSignOut = findViewById(R.id.btn_sign_out);
//        txtAlexaAppLink = findViewById(R.id.alexa_app_link);
        btnSignOut.setOnClickListener(signOutBtnClickListener);
    }

    private View.OnClickListener signOutBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            transport = new SoftAPTransport(hostAddress + ":80");
            security = new Security0();
            session = new Session(transport, security);
            Log.e(TAG, hostAddress);
            session.sessionListener = new Session.SessionListener() {

                @Override
                public void OnSessionEstablished() {
                    Log.d(TAG, "Session established");
                    sendSignOutCommand();
                }

                @Override
                public void OnSessionEstablishFailed(Exception e) {
                    Log.d(TAG, "Session failed");
                }
            };
            session.init(null);
        }
    };

    private void sendSignOutCommand() {

        Avsconfig.CmdSignInStatus configRequest = Avsconfig.CmdSignInStatus.newBuilder()
                .setDummy(123)
                .build();
        Avsconfig.AVSConfigMsgType msgType = Avsconfig.AVSConfigMsgType.TypeCmdSignOut;
        Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.newBuilder()
                .setMsg(msgType)
                .setCmdSigninStatus(configRequest)
                .build();

        byte[] message = security.encrypt(payload.toByteArray());
        transport.sendConfigData(AVS_CONFIG_PATH, message, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {
                Log.e(TAG, "====== On Success of sign out command ======");

                Avsconfig.AVSConfigStatus deviceStatus = processAVSConfigResponse(returnData);
                Log.e(TAG, "Device Status : " + deviceStatus);

                if (deviceStatus.equals(Avsconfig.AVSConfigStatus.Success)) {

                    finish();
                    goToLoginActivity();
                }
            }

            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
            }
        });
    }

    private Avsconfig.AVSConfigStatus processAVSConfigResponse(byte[] responseData) {

        Avsconfig.AVSConfigStatus status = Avsconfig.AVSConfigStatus.InvalidState;
        byte[] decryptedData = this.security.decrypt(responseData);

        try {

            Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.parseFrom(decryptedData);
            Avsconfig.RespSignInStatus signInStatus = payload.getRespSigninStatus();
            status = signInStatus.getStatus();
            Log.d(TAG, "Status message " + status.getNumber() + status.toString());

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return status;
    }

    private void goToLoginActivity() {

        Intent alexaProvisioningIntent = new Intent(getApplicationContext(), LoginWithAmazon.class);
        alexaProvisioningIntent.putExtra(LoginWithAmazon.KEY_HOST_ADDRESS, hostAddress);
        alexaProvisioningIntent.putExtra(LoginWithAmazon.KEY_IS_PROVISIONING, false);
        startActivity(alexaProvisioningIntent);
    }
}
