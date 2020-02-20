package com.espressif.ui.activities;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.espressif.AppConstants;
import com.espressif.provision.R;
import com.espressif.provision.security.Security;
import com.espressif.provision.security.Security0;
import com.espressif.provision.session.Session;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.provision.transport.SoftAPTransport;
import com.espressif.provision.transport.Transport;
import com.espressif.provision.utils.UPnPDevice;
import com.espressif.provision.utils.UPnPDiscovery;
import com.espressif.ui.models.AlexaLocalDevices;
import com.espressif.ui.models.DeviceInfo;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import avs.Avsconfig;
import cn.pedant.SweetAlert.SweetAlertDialog;

public class ScanLocalDevices extends AppCompatActivity {

    private static final String TAG = "Espressif::" + ScanLocalDevices.class.getSimpleName();

    private static int DISCOVERY_TIMEOUT = 5000;
    private static int DEVICE_CONNECT_TIMEOUT = 5000;

    private ListView deviceList;
    private TextView progressText;
    private ProgressBar progressBar;
    private SweetAlertDialog pDialog;

    private ArrayList<AlexaLocalDevices> SSDPdevices;
    private ArrayAdapter<String> SSDPadapter;

    private Session session;
    private Security security;
    private Transport transport;

    private Handler mHandler;

    private UPnPDiscovery discoveryTask;
    private boolean isScanning = false;
    private String deviceHostAddress;
    private String deviceName;
    private ImageView btnScan;

    private Executor threadPoolExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_devices);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_activity_manage_devices);
        setSupportActionBar(toolbar);

        SSDPdevices = new ArrayList<>();
        ArrayList<String> devNames = new ArrayList<>();

        deviceList = findViewById(R.id.connected_devices_list);
        progressBar = findViewById(R.id.devices_scan_progress_indicator);
        progressText = findViewById(R.id.text_loading);
        mHandler = new Handler();

        /*
         * Gets the number of available cores
         * (not always the same as the maximum number of cores)
         */
        int numberOfCores = Runtime.getRuntime().availableProcessors();
        int corePoolSize = numberOfCores + 7;
        int maximumPoolSize = numberOfCores + 10;
        Log.d(TAG, "numberOfCores : " + numberOfCores);
        // Sets the amount of time an idle thread waits before terminating
        final int keepAliveTime = 10;

        // Sets the Time Unit to Milliseconds
        final TimeUnit keepAliveTimeUnit = TimeUnit.SECONDS;

        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>(maximumPoolSize);
        threadPoolExecutor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, keepAliveTimeUnit, workQueue);

        SSDPadapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                android.R.id.text1,
                devNames);
        deviceList.setAdapter(SSDPadapter);
        deviceList.setOnItemClickListener(deviceClickListener);

        btnScan = findViewById(R.id.btn_refresh);
        btnScan.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                Log.d(TAG, "Initiating discovery");
                Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                vib.vibrate(HapticFeedbackConstants.VIRTUAL_KEY);

                isScanning = true;
                updateProgressAndScanBtn();
                searchDevices();
            }
        });

        if (!isScanning) {

            new Handler().postDelayed(new Runnable() {

                @Override
                public void run() {

                    Log.d(TAG, "Initiating discovery");
                    isScanning = true;
                    updateProgressAndScanBtn();
                    searchDevices();
                }
            }, 300);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(runnable);
    }

    private void searchDevices() {

        String customQuery = "M-SEARCH * HTTP/1.1" + "\r\n" +
                "HOST: 239.255.255.250:1900" + "\r\n" +
                "MAN: \"ssdp:discover\"" + "\r\n" +
                "MX: 3" + "\r\n" +
                "ST: urn:schemas-espressif-com:service:Alexa:1" + "\r\n" + // Use for Sonos
//                "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1" + "\r\n" + // Use for Routers
//                "ST: urn:schemas-upnp-org:device:MediaRenderer:1" + "\r\n" + // Use for Routers
                "ST: ssdp:all" + "\r\n" + // Use this for all UPnP Devices (DEFAULT)
                "\r\n";
        int customPort = 1900;
        String customAddress = "239.255.255.250";
        SSDPdevices.clear();
        SSDPadapter.clear();

        discoveryTask = new UPnPDiscovery(this, new UPnPDiscovery.OnDiscoveryListener() {

            @Override
            public void OnStart() {
                Log.d(TAG, "Starting discovery");
            }

            @Override
            public void OnFoundNewDevice(UPnPDevice device) {

                Log.d("ScanLocalDevices", "Found  device: " + device.toString());
                final UPnPDevice foundDevice = device;

                runOnUiThread(new Runnable() {

                    public void run() {

                        boolean deviceExists = false;

                        for (AlexaLocalDevices alreadyHere : SSDPdevices) {

                            if (foundDevice.getHostAddress().equals(alreadyHere.getHostAddress())) {

                                deviceExists = true;
                                SSDPdevices.remove(alreadyHere);
                                SSDPadapter.remove("" + alreadyHere.getFriendlyName());
                                SSDPadapter.notifyDataSetChanged();

                                Log.d(TAG, "Device already exists -" + foundDevice.getST());
                                syncAlexaUpNP(alreadyHere, foundDevice);

                                if (alreadyHere.getFriendlyName() != null) {

                                    SSDPdevices.add(alreadyHere);
                                    SSDPadapter.add("" + alreadyHere.getFriendlyName());
                                    SSDPadapter.notifyDataSetChanged();
                                }
                                break;
                            }
                        }

                        if (!deviceExists) {

                            final AlexaLocalDevices foundAlexa = new AlexaLocalDevices(foundDevice.getHostAddress());
                            syncAlexaUpNP(foundAlexa, foundDevice);

                            Log.d(TAG, "Adding to list adapter " + foundAlexa.getHostAddress());

                            SSDPdevices.add(foundAlexa);
                            SSDPadapter.add("" + foundAlexa.getFriendlyName());
                        }
                    }
                });
            }

            @Override
            public void OnFinish(HashSet<UPnPDevice> devices) {
                Log.d(TAG, "Finish discovery");
                mHandler.removeCallbacks(runnable);
                isScanning = false;
                updateProgressAndScanBtn();
            }

            @Override
            public void OnError(Exception e) {
                Log.e(TAG, "Error: " + e.getLocalizedMessage());
                mHandler.removeCallbacks(runnable);
                isScanning = false;
                updateProgressAndScanBtn();
            }
        }, customQuery, customAddress, customPort);

        discoveryTask.executeOnExecutor(threadPoolExecutor);
        mHandler.postDelayed(runnable, DISCOVERY_TIMEOUT);
    }

    public void syncAlexaUpNP(AlexaLocalDevices foundAlexa, UPnPDevice foundDevice) {

        if (foundAlexa.getHostAddress().equals(foundDevice.getHostAddress())) {
            if (foundDevice.getST().contains("modelno")) {
                foundAlexa.setModelno(foundDevice.getST().replace("modelno:", ""));
            }
            if (foundDevice.getST().contains("softwareversion")) {
                foundAlexa.setSoftwareVersion(foundDevice.getST().replace("softwareversion:", ""));
            }
            if (foundDevice.getST().contains("status")) {
                foundAlexa.setStatus(foundDevice.getST().replace("status:", ""));
            }
            if (foundDevice.getST().contains("friendlyname")) {
                foundAlexa.setFriendlyName(foundDevice.getST().replace("friendlyname:", ""));
            }
        }
    }

    /**
     * This method will update UI (Scan button enable / disable and progressbar visibility)
     */
    private void updateProgressAndScanBtn() {

        if (isScanning) {

            progressBar.setVisibility(View.VISIBLE);
            progressText.setVisibility(View.VISIBLE);
            deviceList.setVisibility(View.GONE);
            btnScan.setVisibility(View.GONE);

        } else {

            progressBar.setVisibility(View.GONE);
            progressText.setVisibility(View.GONE);
            deviceList.setVisibility(View.VISIBLE);
            btnScan.setVisibility(View.VISIBLE);
        }
    }

    private void connectToDevice(AlexaLocalDevices device) {

        deviceHostAddress = device.getHostAddress();
        deviceName = device.getFriendlyName();
        Log.d(TAG, "Device host address : " + deviceHostAddress);
        transport = new SoftAPTransport(deviceHostAddress + ":80");
        security = new Security0();
        session = new Session(this.transport, this.security);

        session.sessionListener = new Session.SessionListener() {

            @Override
            public void OnSessionEstablished() {
                Log.d(TAG, "Session established");
                getDeviceInfo();
            }

            @Override
            public void OnSessionEstablishFailed(Exception e) {
                Log.d(TAG, "Session failed");
                hideProgressDialog();
                Toast.makeText(ScanLocalDevices.this, R.string.error_device_connection_failed, Toast.LENGTH_SHORT).show();
            }
        };
        session.init(null);
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

        byte[] message = security.encrypt(payload.toByteArray());

        transport.sendConfigData(AppConstants.HANDLER_AVS_CONFIG, message, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                DeviceInfo deviceInfo = processDeviceInfoResponse(returnData);
                if (deviceInfo != null) {

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            hideProgressDialog();
                            btnScan.setVisibility(View.VISIBLE);
                        }
                    });
                    mHandler.removeCallbacks(getDeviceInfoFailedTask);
                    goToDeviceActivity(deviceInfo);

                } else {
                    hideProgressDialog();
                    Toast.makeText(ScanLocalDevices.this, R.string.error_get_device_info_not_available, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Error in getting device info");
                e.printStackTrace();
                hideProgressDialog();
                Toast.makeText(ScanLocalDevices.this, R.string.error_get_device_info, Toast.LENGTH_SHORT).show();
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
            }

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return deviceInfo;
    }

    private void getSignedInStatus() {

        transport = new SoftAPTransport(deviceHostAddress + ":80");
        security = new Security0();
        session = new Session(this.transport, this.security);

        session.sessionListener = new Session.SessionListener() {

            @Override
            public void OnSessionEstablished() {
                Log.d(TAG, "Session established");
                getAlexaSignedInStatus();
            }

            @Override
            public void OnSessionEstablishFailed(Exception e) {
                Log.d(TAG, "Session failed");
                hideProgressDialog();
                Toast.makeText(ScanLocalDevices.this, R.string.error_device_connection_failed, Toast.LENGTH_SHORT).show();
            }
        };
        session.init(null);
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
        byte[] message = security.encrypt(payload.toByteArray());

        transport.sendConfigData(AppConstants.HANDLER_AVS_CONFIG, message, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Avsconfig.AVSConfigStatus deviceStatus = processSignInStatusResponse(returnData);
                Log.d(TAG, "SignIn Status Received : " + deviceStatus);
                hideProgressDialog();

                if (deviceStatus.equals(Avsconfig.AVSConfigStatus.SignedIn)) {
                    goToAlexaActivity();
                } else {
                    goToLoginActivity();
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Error in getting status");
                e.printStackTrace();

                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        hideProgressDialog();
                        alertForDeviceConnectionFailed();
                    }
                });
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

    private void goToLoginActivity() {

        Intent alexaProvisioningIntent = new Intent(getApplicationContext(), LoginWithAmazon.class);
        alexaProvisioningIntent.putExtra(LoginWithAmazon.KEY_HOST_ADDRESS, deviceHostAddress);
        alexaProvisioningIntent.putExtra(LoginWithAmazon.KEY_DEVICE_NAME, deviceName);
        alexaProvisioningIntent.putExtra(LoginWithAmazon.KEY_IS_PROVISIONING, false);
        startActivity(alexaProvisioningIntent);
    }

    private void goToAlexaActivity() {

        Intent alexaIntent = new Intent(getApplicationContext(), AlexaActivity.class);
        alexaIntent.putExtra(LoginWithAmazon.KEY_HOST_ADDRESS, deviceHostAddress);
        alexaIntent.putExtra(LoginWithAmazon.KEY_DEVICE_NAME, deviceName);
        alexaIntent.putExtras(getIntent());
        startActivity(alexaIntent);
    }

    private void goToDeviceActivity(DeviceInfo deviceInfo) {

        Intent alexaIntent = new Intent(getApplicationContext(), DeviceActivity.class);
        alexaIntent.putExtra(LoginWithAmazon.KEY_HOST_ADDRESS, deviceHostAddress);
        alexaIntent.putExtra(AppConstants.KEY_DEVICE_NAME, deviceName);
        alexaIntent.putExtra(AppConstants.KEY_DEVICE_INFO, deviceInfo);
        alexaIntent.putExtras(getIntent());
        startActivity(alexaIntent);
    }

    private void alertForDeviceConnectionFailed() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);

        builder.setTitle("Error!");
        builder.setMessage(R.string.error_device_connection_failed);

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_ok, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {

                dialog.dismiss();
            }
        });

        builder.show();
    }

    private Runnable runnable = new Runnable() {

        @Override
        public void run() {

            Log.e(TAG, "Timeout occurred.");
            discoveryTask.cancel(true);
            isScanning = false;
            updateProgressAndScanBtn();
        }
    };

    private AdapterView.OnItemClickListener deviceClickListener = new AdapterView.OnItemClickListener() {

        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {

            Log.e(TAG, "" + SSDPdevices.get(position).getFriendlyName() + " Device clicked");
            btnScan.setVisibility(View.VISIBLE);
            String progressMsg = getString(R.string.progress_connect_device);
            showProgressDialog(progressMsg);
            connectToDevice(SSDPdevices.get(position));
            mHandler.postDelayed(getDeviceInfoFailedTask, DEVICE_CONNECT_TIMEOUT);
        }
    };

    private Runnable getDeviceInfoFailedTask = new Runnable() {

        @Override
        public void run() {
            Log.e(TAG, "Not able to get Device info");
            getSignedInStatus();
//            hideProgressDialog();
//            alertForDeviceConnectionFailed();
        }
    };

    private void showProgressDialog(String message) {

        if (pDialog == null || !pDialog.isShowing()) {
            pDialog = new SweetAlertDialog(this, SweetAlertDialog.PROGRESS_TYPE);
            pDialog.getProgressHelper().setBarColor(Color.parseColor("#A5DC86"));
            pDialog.setTitleText(message);
            pDialog.setCancelable(false);
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
