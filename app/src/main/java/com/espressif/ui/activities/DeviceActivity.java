package com.espressif.ui.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.espressif.AppConstants;
import com.espressif.provision.R;
import com.espressif.provision.security.Security;
import com.espressif.provision.security.Security0;
import com.espressif.provision.security.Security1;
import com.espressif.provision.session.Session;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.provision.transport.SoftAPTransport;
import com.espressif.provision.transport.Transport;
import com.espressif.ui.models.DeviceInfo;
import com.google.protobuf.InvalidProtocolBufferException;

import avs.Avsconfig;
import cn.pedant.SweetAlert.SweetAlertDialog;

public class DeviceActivity extends AppCompatActivity {

    private static final String TAG = DeviceActivity.class.getSimpleName();

    private static final int REQUEST_CODE_LANGUAGE = 1;

    private Toolbar toolbar;
    private TextView tvDeviceName;
    private SeekBar volumeBar;
    private TextView tvLanguage;

    private RelativeLayout rlDeviceName;
    private RelativeLayout rlVolume;
    private RelativeLayout rlSound;
    private RelativeLayout rlLanguage;
    private RelativeLayout rlAbout;
    private RelativeLayout rlManageAccount;
    private SweetAlertDialog pDialog;

    private DeviceInfo deviceInfo;
    private String deviceHostAddress;
    private String deviceName;
    private boolean isNewFw;

    private Session session;
    private Security security;
    private Transport transport;

    private boolean isAvsSignedIn = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);

        Intent intent = getIntent();
        deviceHostAddress = intent.getStringExtra(LoginWithAmazon.KEY_HOST_ADDRESS);
        deviceName = intent.getStringExtra(LoginWithAmazon.KEY_DEVICE_NAME);
        deviceInfo = intent.getParcelableExtra(AppConstants.KEY_DEVICE_INFO);
        isNewFw = deviceInfo.isNewFirmware();

        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(deviceName);
        setSupportActionBar(toolbar);

        initViews();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && requestCode == REQUEST_CODE_LANGUAGE && data != null) {

            deviceInfo.setLanguage(data.getIntExtra(AppConstants.KEY_DEVICE_LANGUAGE, 4)); // (default language en-In, so taken default value as 4)
            String[] languages = getResources().getStringArray(R.array.language_array);
            tvLanguage.setText(languages[deviceInfo.getLanguage()]);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        transport = new SoftAPTransport(deviceHostAddress + ":80");
        if (deviceInfo.isNewFirmware()) {
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
                getDeviceInfo();
            }

            @Override
            public void OnSessionEstablishFailed(Exception e) {
                Log.d(TAG, "Session failed");
                e.printStackTrace();
            }
        };
    }

    private void initViews() {

        volumeBar = findViewById(R.id.volume_seekbar);
        rlDeviceName = findViewById(R.id.layout_device_name);
        rlVolume = findViewById(R.id.layout_volume);
        rlSound = findViewById(R.id.layout_sound);
        rlLanguage = findViewById(R.id.layout_language);
        rlAbout = findViewById(R.id.layout_about);
        rlManageAccount = findViewById(R.id.layout_manage_account);

        tvDeviceName = findViewById(R.id.tv_device_name);
        tvLanguage = findViewById(R.id.tv_language);

        tvDeviceName.setText(deviceInfo.getDeviceName());
        volumeBar.setProgress(deviceInfo.getVolume());
        String[] languages = getResources().getStringArray(R.array.language_array);
        tvLanguage.setText(languages[deviceInfo.getLanguage()]);

        volumeBar.setOnSeekBarChangeListener(volumeChangeListener);
        rlDeviceName.setOnClickListener(deviceNameClickListener);
        rlSound.setOnClickListener(soundClickListener);
        rlLanguage.setOnClickListener(languageClickListener);
        rlAbout.setOnClickListener(aboutClickListener);
        rlManageAccount.setOnClickListener(manageAccountClickListener);
    }

    private void updateUI() {

        tvDeviceName.setText(deviceInfo.getDeviceName());
        toolbar.setTitle(deviceInfo.getDeviceName());
        volumeBar.setProgress(deviceInfo.getVolume());
        String[] languages = getResources().getStringArray(R.array.language_array);
        tvLanguage.setText(languages[deviceInfo.getLanguage()]);
    }

    private SeekBar.OnSeekBarChangeListener volumeChangeListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            Log.e(TAG, "onProgressChanged : " + progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(final SeekBar seekBar) {

            Log.e(TAG, "onStopTrackingTouch : " + seekBar.getProgress());
            transport = new SoftAPTransport(deviceHostAddress + ":80");
            if (deviceInfo.isNewFirmware()) {
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
                    changeVolume(seekBar.getProgress());
                }

                @Override
                public void OnSessionEstablishFailed(Exception e) {
                    Log.d(TAG, "Session failed");
                }
            };
        }
    };

    private View.OnClickListener deviceNameClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            askForDeviceName();
        }
    };

    private View.OnClickListener soundClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            Intent soundIntent = new Intent(DeviceActivity.this, SoundActivity.class);
            soundIntent.putExtra(AppConstants.KEY_DEVICE_INFO, deviceInfo);
            soundIntent.putExtra(LoginWithAmazon.KEY_HOST_ADDRESS, deviceHostAddress);
            startActivity(soundIntent);
        }
    };

    private View.OnClickListener languageClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Intent languageIntent = new Intent(DeviceActivity.this, LanguageListActivity.class);
            languageIntent.putExtra(LoginWithAmazon.KEY_HOST_ADDRESS, deviceHostAddress);
            languageIntent.putExtra(AppConstants.KEY_DEVICE_INFO, deviceInfo);
            startActivityForResult(languageIntent, REQUEST_CODE_LANGUAGE);
        }
    };

    private View.OnClickListener aboutClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            Intent deviceInfoIntent = new Intent(DeviceActivity.this, DeviceInfoActivity.class);
            deviceInfoIntent.putExtra(AppConstants.KEY_DEVICE_INFO, deviceInfo);
            startActivity(deviceInfoIntent);
        }
    };

    private View.OnClickListener manageAccountClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            if (isAvsSignedIn) {
                goToAlexaActivity();
            } else {
                goToLoginActivity();
            }
        }
    };

    private void askForDeviceName() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);

        LayoutInflater layoutInflaterAndroid = LayoutInflater.from(this);
        View view = layoutInflaterAndroid.inflate(R.layout.dialog_device_name, null);
        builder.setView(view);
        final EditText etDeviceName = view.findViewById(R.id.et_device_name);
        etDeviceName.setText(deviceName);
        etDeviceName.setSelection(etDeviceName.getText().length());

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_save, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                String newDeviceName = etDeviceName.getText().toString();

                if (newDeviceName != null) {
                    newDeviceName = newDeviceName.trim();
                }

                if (TextUtils.isEmpty(newDeviceName)) {
                    Toast.makeText(DeviceActivity.this, R.string.error_device_name_empty, Toast.LENGTH_SHORT).show();
                    return;
                } else if (newDeviceName.contains("::")) {
                    String errMsg = "Please enter a valid name which does not contain ::";
                    Toast.makeText(DeviceActivity.this, errMsg, Toast.LENGTH_SHORT).show();
                    return;
                }

                transport = new SoftAPTransport(deviceHostAddress + ":80");
                if (deviceInfo.isNewFirmware()) {
                    security = new Security1(getResources().getString(R.string.proof_of_possesion));
                } else {
                    security = new Security0();
                }
                session = new Session(transport, security);
                session.init(null);

                final String finalNewDeviceName = newDeviceName;
                session.sessionListener = new Session.SessionListener() {

                    @Override
                    public void OnSessionEstablished() {

                        Log.d(TAG, "Session established");
                        setDeviceName(finalNewDeviceName);
                    }

                    @Override
                    public void OnSessionEstablishFailed(Exception e) {
                        Log.d(TAG, "Session failed");
                    }
                };
            }
        });

        builder.setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });

        builder.show();
    }

    private void setDeviceName(final String newDeviceName) {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                String progressMsg = getString(R.string.progress_set_device_name);
                showProgressDialog(progressMsg);
            }
        });

        Avsconfig.CmdSetUserVisibleName changeNameRequest = Avsconfig.CmdSetUserVisibleName.newBuilder()
                .setName(newDeviceName)
                .build();
        Avsconfig.AVSConfigMsgType msgType = Avsconfig.AVSConfigMsgType.TypeCmdSetUserVisibleName;
        Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.newBuilder()
                .setMsg(msgType)
                .setCmdUserVisibleName(changeNameRequest)
                .build();

        byte[] message = this.security.encrypt(payload.toByteArray());

        this.transport.sendConfigData(AppConstants.HANDLER_AVS_CONFIG, message, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Log.e(TAG, "Device name change success");

                Avsconfig.AVSConfigStatus responseStatus = processDeviceNameChangeResponse(returnData);
                Log.e(TAG, "Device name change status : " + responseStatus);

                if (responseStatus == Avsconfig.AVSConfigStatus.Success) {

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            deviceName = newDeviceName;
                            deviceInfo.setDeviceName(newDeviceName);
                            tvDeviceName.setText(deviceInfo.getDeviceName());
                            toolbar.setTitle(newDeviceName);
                            hideProgressDialog();
                        }
                    });
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Device name change failed");
                e.printStackTrace();
                hideProgressDialog();
                Toast.makeText(DeviceActivity.this, R.string.error_get_device_info, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Avsconfig.AVSConfigStatus processDeviceNameChangeResponse(byte[] responseData) {

        Avsconfig.AVSConfigStatus status = Avsconfig.AVSConfigStatus.InvalidState;
        byte[] decryptedData = this.security.decrypt(responseData);

        try {

            Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.parseFrom(decryptedData);
            Avsconfig.RespSetUserVisibleName response = payload.getRespUserVisibleName();
            status = response.getStatus();
            Log.d(TAG, "Status message " + status.getNumber() + status.toString());

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return status;
    }

    private void changeVolume(final int volume) {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                String progressMsg = getString(R.string.progress_set_volume);
                showProgressDialog(progressMsg);
            }
        });

        Avsconfig.CmdSetVolume volumeChangeRequest = Avsconfig.CmdSetVolume.newBuilder()
                .setLevel(volume)
                .build();
        Avsconfig.AVSConfigMsgType msgType = Avsconfig.AVSConfigMsgType.TypeCmdSetVolume;
        Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.newBuilder()
                .setMsg(msgType)
                .setCmdSetVolume(volumeChangeRequest)
                .build();

        byte[] message = this.security.encrypt(payload.toByteArray());
        this.transport.sendConfigData(AppConstants.HANDLER_AVS_CONFIG, message, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Log.e(TAG, "Alexa tone msg sent");
                Avsconfig.AVSConfigStatus responseStatus = processVolumeChangeResponse(returnData);

                if (responseStatus == Avsconfig.AVSConfigStatus.Success) {

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {

                            deviceInfo.setVolume(volume);
                            hideProgressDialog();
                        }
                    });
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Error in changing language");
                e.printStackTrace();
                hideProgressDialog();
                Toast.makeText(DeviceActivity.this, R.string.error_language_change, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Avsconfig.AVSConfigStatus processVolumeChangeResponse(byte[] responseData) {

        Avsconfig.AVSConfigStatus status = Avsconfig.AVSConfigStatus.InvalidState;
        byte[] decryptedData = this.security.decrypt(responseData);

        try {

            Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.parseFrom(decryptedData);
            Avsconfig.RespSetVolume response = payload.getRespSetVolume();
            status = response.getStatus();
            Log.d(TAG, "Volume change message Status : " + status.toString());

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return status;
    }

    private void getAlexaSignedInStatus() {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                String progressMsg = getString(R.string.progress_get_status);
                showProgressDialog(progressMsg);
            }
        });

        Avsconfig.CmdSignInStatus configRequest = Avsconfig.CmdSignInStatus.newBuilder()
                .setDummy(123)
                .build();
        Avsconfig.AVSConfigMsgType msgType = Avsconfig.AVSConfigMsgType.TypeCmdSignInStatus;
        Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.newBuilder()
                .setMsg(msgType)
                .setCmdSigninStatus(configRequest)
                .build();
        byte[] message = this.security.encrypt(payload.toByteArray());

        this.transport.sendConfigData(AppConstants.HANDLER_AVS_CONFIG, message, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Avsconfig.AVSConfigStatus deviceStatus = processSignInStatusResponse(returnData);
                Log.d(TAG, "SignIn Status Received : " + deviceStatus);
                hideProgressDialog();

                if (deviceStatus.equals(Avsconfig.AVSConfigStatus.SignedIn)) {
                    isAvsSignedIn = true;
                } else {
                    isAvsSignedIn = false;
                }

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        enableAlexaFeatures();
                    }
                });
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Error in getting status");
                e.printStackTrace();
            }
        });
    }

    private Avsconfig.AVSConfigStatus processSignInStatusResponse(byte[] responseData) {

        Avsconfig.AVSConfigStatus status = Avsconfig.AVSConfigStatus.InvalidState;
        byte[] decryptedData = this.security.decrypt(responseData);

        try {

            Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.parseFrom(decryptedData);
            Avsconfig.RespSignInStatus signInStatus = payload.getRespSigninStatus();
            status = signInStatus.getStatus();
            Log.d(TAG, "SignIn Status message " + status.toString());

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return status;
    }

    private void getDeviceInfo() {

        runOnUiThread(new Runnable() {

            @Override
            public void run() {

                String progressMsg = getString(R.string.progress_get_device_info);
                showProgressDialog(progressMsg);
            }
        });

        Avsconfig.CmdGetDeviceInfo deviceInfoRequest = Avsconfig.CmdGetDeviceInfo.newBuilder()
                .setDummy(123)
                .build();
        Avsconfig.AVSConfigMsgType msgType = Avsconfig.AVSConfigMsgType.TypeCmdGetDeviceInfo;
        Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.newBuilder()
                .setMsg(msgType)
                .setCmdGetDeviceInfo(deviceInfoRequest)
                .build();

        byte[] message = this.security.encrypt(payload.toByteArray());

        this.transport.sendConfigData(AppConstants.HANDLER_AVS_CONFIG, message, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                DeviceInfo device = processDeviceInfoResponse(returnData);
                if (device != null) {

                    deviceInfo = device;
                    deviceInfo.setNewFirmware(isNewFw);
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            updateUI();
                            getAlexaSignedInStatus();
                        }
                    });

                } else {
                    hideProgressDialog();
                    Toast.makeText(DeviceActivity.this, R.string.error_get_device_info_not_available, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Error in getting status");
                e.printStackTrace();
                hideProgressDialog();
                Toast.makeText(DeviceActivity.this, R.string.error_get_device_info, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private DeviceInfo processDeviceInfoResponse(byte[] responseData) {

        byte[] decryptedData = this.security.decrypt(responseData);
        DeviceInfo deviceInfo = null;

        try {
            Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.parseFrom(decryptedData);
            Avsconfig.RespGetDeviceInfo response = payload.getRespGetDeviceInfo();
            Avsconfig.AVSGenericDeviceInfo genericInfo = response.getGenericInfo();
            Avsconfig.AVSSpecificDeviceInfo specificInfo = response.getAVSSpecificInfo();

            Log.e(TAG, "Status : " + response.getStatus());

            if (response.getStatus().equals(Avsconfig.AVSConfigStatus.Success)) {

                deviceInfo = new DeviceInfo();
                deviceInfo.setDeviceName(genericInfo.getUserVisibleName());
                deviceInfo.setConnectedWifi(genericInfo.getWiFi());
                deviceInfo.setFwVersion(genericInfo.getFwVersion());
                deviceInfo.setDeviceIp(deviceHostAddress);
                deviceInfo.setMac(genericInfo.getMAC());
                deviceInfo.setSerialNumber(genericInfo.getSerialNum());
                deviceInfo.setStartToneEnabled(specificInfo.getSORAudioCue());
                deviceInfo.setEndToneEnabled(specificInfo.getEORAudioCue());
                deviceInfo.setVolume(specificInfo.getVolume());
                deviceInfo.setLanguage(specificInfo.getAssistantLangValue());
                deviceInfo.setNewFirmware(isNewFw);
            }

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return deviceInfo;
    }

    private void enableAlexaFeatures() {

        if (isAvsSignedIn) {

            rlVolume.setClickable(true);
            rlSound.setClickable(true);
            rlLanguage.setClickable(true);
            volumeBar.setEnabled(true);
            rlVolume.setAlpha(1f);
            rlSound.setAlpha(1f);
            rlLanguage.setAlpha(1f);

        } else {

            rlVolume.setClickable(false);
            rlSound.setClickable(false);
            rlLanguage.setClickable(false);
            volumeBar.setEnabled(false);
            rlVolume.setAlpha(0.5f);
            rlSound.setAlpha(0.5f);
            rlLanguage.setAlpha(0.5f);
        }
    }

    private void goToLoginActivity() {

        Intent alexaProvisioningIntent = new Intent(getApplicationContext(), LoginWithAmazon.class);
        alexaProvisioningIntent.putExtra(LoginWithAmazon.KEY_HOST_ADDRESS, deviceHostAddress);
        alexaProvisioningIntent.putExtra(LoginWithAmazon.KEY_DEVICE_NAME, deviceName);
        alexaProvisioningIntent.putExtra(LoginWithAmazon.KEY_IS_PROVISIONING, false);
        alexaProvisioningIntent.putExtra(AppConstants.KEY_IS_NEW_FIRMWARE, deviceInfo.isNewFirmware());
        startActivity(alexaProvisioningIntent);
    }

    private void goToAlexaActivity() {

        Intent alexaIntent = new Intent(getApplicationContext(), AlexaActivity.class);
        alexaIntent.putExtra(LoginWithAmazon.KEY_HOST_ADDRESS, deviceHostAddress);
        alexaIntent.putExtra(LoginWithAmazon.KEY_DEVICE_NAME, deviceName);
        alexaIntent.putExtra(AppConstants.KEY_IS_NEW_FIRMWARE, deviceInfo.isNewFirmware());
        alexaIntent.putExtras(getIntent());
        startActivity(alexaIntent);
    }

    private void showProgressDialog(String message) {

        if (pDialog == null || !pDialog.isShowing()) {
            pDialog = new SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE);
            pDialog.getProgressHelper().setBarColor(Color.parseColor("#A5DC86"));
            pDialog.setTitleText(message);
            pDialog.setCancelable(true);
            pDialog.show();
        } else {
            pDialog.setTitleText(message);
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
