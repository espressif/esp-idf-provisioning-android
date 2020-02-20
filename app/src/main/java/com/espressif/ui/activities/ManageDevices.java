package com.espressif.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.espressif.provision.R;
import com.espressif.provision.security.Security;
import com.espressif.provision.security.Security0;
import com.espressif.provision.session.Session;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.provision.transport.SoftAPTransport;
import com.espressif.provision.transport.Transport;
import com.espressif.ui.models.AlexaLocalDevices;

import avs.Avsconfig;

import static com.espressif.avs.ConfigureAVS.AVS_CONFIG_PATH;

public class ManageDevices extends AppCompatActivity {
    private AlexaLocalDevices device;
    private Session ses;
    private Transport tras;
    private Security secu;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_local_device);
        Intent i = getIntent();
        this.device= (AlexaLocalDevices) i.getSerializableExtra("device");

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("Manage "+device.getFriendlyName());

        TextView t = findViewById(R.id.deviceInfo);

        t.setText("Device Info: \n"+"IP Address\t-\t"+device.getHostAddress());
//        +"\nModel no-"+device.getModelno());
        Button logout = findViewById(R.id.logout_button);
        logout.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                establishSession();
            }
        });

        this.tras = new SoftAPTransport(device.getHostAddress()+":80");
        this.secu = new Security0();
        this.ses = new Session(this.tras, this.secu);
        this.ses.sessionListener = new Session.SessionListener() {
            @Override
            public void OnSessionEstablished(){
                Log.d("ManageDevices","Session established");
                getStatus();
            }
            @Override
            public void OnSessionEstablishFailed(Exception e){
                Log.d("ManageDevices","Session failed");
            }
        };
    }
    private void establishSession(){

        this.ses.init(null);
        if (this.ses.isEstablished()) {
            // Check signin status
            getStatus();
        }
    }
    private void getStatus(){
        Avsconfig.CmdSignInStatus configRequest = Avsconfig.CmdSignInStatus.newBuilder()
                .setDummy(123)
                .build();
        Avsconfig.AVSConfigMsgType msgType = Avsconfig.AVSConfigMsgType.TypeCmdSignInStatus;
        Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.newBuilder()
                .setMsg(msgType)
                .setCmdSigninStatus(configRequest)
                .build();
        byte[] message = this.secu.encrypt(payload.toByteArray());
        this.tras.sendConfigData(AVS_CONFIG_PATH, message, new ResponseListener(){
            @Override
            public void onSuccess(byte[] returnData) {
//                DeviceDetails = processSetAVSConfigResponse(returnData, configureAVSActionListener);
                Log.d("ManageDevices","Sent SigninStatus message");
            }
            @Override
            public void onFailure(Exception e) {
                Log.d("ManageDevices","Fatla");
            }

        });
    }
}

