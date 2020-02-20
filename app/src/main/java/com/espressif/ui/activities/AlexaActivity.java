package com.espressif.ui.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.espressif.AppConstants;
import com.espressif.provision.R;
import com.espressif.provision.security.Security;
import com.espressif.provision.security.Security0;
import com.espressif.provision.security.Security1;
import com.espressif.provision.session.Session;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.provision.transport.SoftAPTransport;
import com.espressif.provision.transport.Transport;
import com.espressif.ui.widgets.AvsEmberLightText;
import com.google.protobuf.InvalidProtocolBufferException;

import avs.Avsconfig;

import static com.espressif.avs.ConfigureAVS.AVS_CONFIG_PATH;

public class AlexaActivity extends AppCompatActivity {

    private static final String TAG = "Espressif:" + AlexaActivity.class.getSimpleName();

    private AvsEmberLightText txtAlexaAppLink;

    private Session session;
    private Security security;
    private Transport transport;

    private String hostAddress;
    private String deviceName;

    private TextView txtDeviceName;
    private boolean isProv = false, isNewFw = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alexa);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent intent = getIntent();
        hostAddress = intent.getStringExtra(LoginWithAmazon.KEY_HOST_ADDRESS);
        deviceName = intent.getStringExtra(LoginWithAmazon.KEY_DEVICE_NAME);
        isProv = intent.getBooleanExtra("is_prov", false);
        isNewFw = intent.getBooleanExtra(AppConstants.KEY_IS_NEW_FIRMWARE, false);
        initViews();

        if (!TextUtils.isEmpty(deviceName)) {
            txtDeviceName.setText(deviceName);
        }

        if (!isProv) {

            transport = new SoftAPTransport(hostAddress + ":80");

            if (isNewFw) {
                security = new Security1(getResources().getString(R.string.proof_of_possesion));
            } else {
                security = new Security0();
            }
            session = new Session(transport, security);
            session.init(null);

            session.sessionListener = new Session.SessionListener() {

                @Override
                public void OnSessionEstablished() {
                    Log.d(TAG, "Session established");
                }

                @Override
                public void OnSessionEstablishFailed(Exception e) {
                    Log.d(TAG, "Session failed");
                }
            };
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.

        if (isProv) {
            getMenuInflater().inflate(R.menu.menu_alexa_prov, menu);
        } else {
            getMenuInflater().inflate(R.menu.menu_alexa, menu);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_sign_out) {

            if (session.isEstablished()) {
                sendSignOutCommand();
            }
            return true;
        } else if (id == R.id.action_done) {

            finish();
            BLEProvisionLanding.isBleWorkDone = true;
            Intent intent = new Intent(getApplicationContext(), EspMainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initViews() {

        txtDeviceName = findViewById(R.id.txt_device_name);
        txtAlexaAppLink = findViewById(R.id.alexa_app_link);

        SpannableString stringForAlexaAppLink = new SpannableString("To learn more and access additional features, download the Alexa App.");

        ClickableSpan clickableSpan = new ClickableSpan() {

            @Override
            public void onClick(View textView) {
                textView.invalidate();
                openAlexaApp();
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(getResources().getColor(R.color.alexa_color));
                ds.setUnderlineText(false);
            }
        };
        stringForAlexaAppLink.setSpan(clickableSpan, 59, 68, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        txtAlexaAppLink.setText(stringForAlexaAppLink);
        txtAlexaAppLink.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void openAlexaApp() {
        openApplication("com.amazon.dee.app");
    }

    public void openApplication(String packageN) {
        Intent i = getPackageManager().getLaunchIntentForPackage(packageN);
        if (i != null) {
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            startActivity(i);
        } else {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageN)));
            } catch (android.content.ActivityNotFoundException anfe) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + packageN)));
            }
        }
    }

    private View.OnClickListener signOutBtnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vib.vibrate(HapticFeedbackConstants.VIRTUAL_KEY);

            if (session.isEstablished()) {
                sendSignOutCommand();
            }
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
        alexaProvisioningIntent.putExtra(LoginWithAmazon.KEY_DEVICE_NAME, deviceName);
        alexaProvisioningIntent.putExtra(LoginWithAmazon.KEY_IS_PROVISIONING, false);
        alexaProvisioningIntent.putExtra(AppConstants.KEY_IS_NEW_FIRMWARE, isNewFw);
        startActivity(alexaProvisioningIntent);
    }
}
