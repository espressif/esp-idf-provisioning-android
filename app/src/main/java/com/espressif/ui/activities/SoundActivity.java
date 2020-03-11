package com.espressif.ui.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.espressif.AppConstants;
import com.espressif.provision.R;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.ui.models.DeviceInfo;
import com.google.protobuf.InvalidProtocolBufferException;

import avs.Avsconfig;
import cn.pedant.SweetAlert.SweetAlertDialog;

public class SoundActivity extends AppCompatActivity {

    private static final String TAG = SoundActivity.class.getSimpleName();

    private SweetAlertDialog pDialog;
    private Switch switchStartTone, switchEndTone;

    private DeviceInfo deviceInfo;
    private String deviceHostAddress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sound);

        Intent intent = getIntent();
        deviceHostAddress = intent.getStringExtra(LoginWithAmazon.KEY_HOST_ADDRESS);
        deviceInfo = intent.getParcelableExtra(AppConstants.KEY_DEVICE_INFO);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_activity_sound);
        setSupportActionBar(toolbar);

        initViews();
    }

    private void initViews() {

        switchStartTone = findViewById(R.id.switch_start_tone);
        switchEndTone = findViewById(R.id.switch_end_tone);

        switchStartTone.setChecked(deviceInfo.isStartToneEnabled());
        switchEndTone.setChecked(deviceInfo.isEndToneEnabled());

        switchStartTone.setOnCheckedChangeListener(startToneChangeListener);
        switchEndTone.setOnCheckedChangeListener(endToneChangeListener);
    }

    private CompoundButton.OnCheckedChangeListener startToneChangeListener = new CompoundButton.OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {

            sendStartOfToneRequest(isChecked);
        }
    };

    private CompoundButton.OnCheckedChangeListener endToneChangeListener = new CompoundButton.OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, final boolean isChecked) {

            sendEndOfToneRequest(isChecked);
        }
    };

    private void sendStartOfToneRequest(final boolean status) {

        final String progressMsg = getString(R.string.progress_set_alexa_tone);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showProgressDialog(progressMsg);
            }
        });

        Avsconfig.CmdSetSORAudioCue alexaToneChangeRequest = Avsconfig.CmdSetSORAudioCue.newBuilder()
                .setAudioCue(status)
                .build();
        Avsconfig.AVSConfigMsgType msgType = Avsconfig.AVSConfigMsgType.TypeCmdSetSORAudioCue;
        Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.newBuilder()
                .setMsg(msgType)
                .setCmdSorAudioCue(alexaToneChangeRequest)
                .build();

        byte[] message = ScanLocalDevices.security.encrypt(payload.toByteArray());
        ScanLocalDevices.transport.sendConfigData(AppConstants.HANDLER_AVS_CONFIG, message, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Log.d(TAG, "Alexa tone msg sent");
                Avsconfig.AVSConfigStatus responseStatus = processStartToneChangeResponse(returnData);

                if (responseStatus == Avsconfig.AVSConfigStatus.Success) {

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            switchStartTone.setChecked(status);
                            deviceInfo.setStartToneEnabled(status);
                            hideProgressDialog();
                        }
                    });
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Error in changing alexa tone");
                e.printStackTrace();
                hideProgressDialog();
                Toast.makeText(SoundActivity.this, R.string.error_alexa_tone, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Avsconfig.AVSConfigStatus processStartToneChangeResponse(byte[] responseData) {

        Avsconfig.AVSConfigStatus status = Avsconfig.AVSConfigStatus.InvalidState;
        byte[] decryptedData = ScanLocalDevices.security.decrypt(responseData);

        try {

            Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.parseFrom(decryptedData);
            Avsconfig.RespSetSORAudioCue response = payload.getRespSorAudioCue();
            status = response.getStatus();
            Log.d(TAG, "Start of request message status : " + status.toString());

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return status;
    }

    private void sendEndOfToneRequest(final boolean status) {

        final String progressMsg = getString(R.string.progress_set_alexa_tone);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showProgressDialog(progressMsg);
            }
        });

        Avsconfig.CmdSetEORAudioCue alexaToneChangeRequest = Avsconfig.CmdSetEORAudioCue.newBuilder()
                .setAudioCue(status)
                .build();
        Avsconfig.AVSConfigMsgType msgType = Avsconfig.AVSConfigMsgType.TypeCmdSetEORAudioCue;
        Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.newBuilder()
                .setMsg(msgType)
                .setCmdEorAudioCue(alexaToneChangeRequest)
                .build();

        byte[] message = ScanLocalDevices.security.encrypt(payload.toByteArray());
        ScanLocalDevices.transport.sendConfigData(AppConstants.HANDLER_AVS_CONFIG, message, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Log.d(TAG, "Alexa tone msg sent");
                Avsconfig.AVSConfigStatus responseStatus = processEndToneChangeResponse(returnData);

                if (responseStatus == Avsconfig.AVSConfigStatus.Success) {

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            switchEndTone.setChecked(status);
                            deviceInfo.setEndToneEnabled(status);
                            hideProgressDialog();
                        }
                    });
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Error in changing alexa tone");
                e.printStackTrace();
                hideProgressDialog();
                Toast.makeText(SoundActivity.this, R.string.error_alexa_tone, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Avsconfig.AVSConfigStatus processEndToneChangeResponse(byte[] responseData) {

        Avsconfig.AVSConfigStatus status = Avsconfig.AVSConfigStatus.InvalidState;
        byte[] decryptedData = ScanLocalDevices.security.decrypt(responseData);

        try {

            Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.parseFrom(decryptedData);
            Avsconfig.RespSetEORAudioCue response = payload.getRespEorAudioCue();
            status = response.getStatus();
            Log.d(TAG, "End of request message status : " + status.toString());

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return status;
    }

    private void showProgressDialog(String message) {

        if (pDialog == null || !pDialog.isShowing()) {
            pDialog = new SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE);
            pDialog.getProgressHelper().setBarColor(Color.parseColor("#A5DC86"));
            pDialog.setTitleText(message);
            pDialog.setCancelable(true);
            pDialog.show();
        }
    }

    private void hideProgressDialog() {

        if (pDialog != null) {
            pDialog.dismiss();
            pDialog = null;
        }
    }
}
