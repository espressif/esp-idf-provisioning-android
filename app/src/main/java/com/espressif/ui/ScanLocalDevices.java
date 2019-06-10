package com.espressif.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.espressif.provision.R;
import com.espressif.provision.security.Security;
import com.espressif.provision.security.Security0;
import com.espressif.provision.session.Session;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.provision.transport.SoftAPTransport;
import com.espressif.provision.transport.Transport;
import com.espressif.provision.utils.UPnPDevice;
import com.espressif.provision.utils.UPnPDiscovery;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.HashSet;

import avs.Avsconfig;

import static com.espressif.avs.ConfigureAVS.AVS_CONFIG_PATH;

public class ScanLocalDevices extends AppCompatActivity {

    private static final String TAG = "Espressif::" + ScanLocalDevices.class.getSimpleName();

    private static int DISCOVERY_TIMEOUT = 5000;

    private ListView deviceList;
    private Button btnScanDevices;
    private TextView progressText;
    private ProgressBar progressBar;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_devices);

        Toolbar toolbar = (Toolbar) findViewById(R.id.scantoolbar);
        toolbar.setTitle("Devices on Local Network");

        SSDPdevices = new ArrayList<>();
        ArrayList<String> devNames = new ArrayList<>();

        deviceList = findViewById(R.id.connected_devices_list);
        progressBar = findViewById(R.id.devices_scan_progress_indicator);
        progressText = findViewById(R.id.text_loading);
        mHandler = new Handler();

        SSDPadapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                android.R.id.text1,
                devNames);
        deviceList.setAdapter(SSDPadapter);
        deviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {

//                progressBar.setVisibility(View.VISIBLE);
//                Log.d("WiFiScanList","Device to be connected -"+SSDPdevices.get(pos));
                deviceList.setVisibility(View.GONE);
                btnScanDevices.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);

                String progressMsg = getString(R.string.progress_get_status) + " " + SSDPdevices.get(position).getFriendlyName();
                progressText.setText(progressMsg);
                progressText.setVisibility(View.VISIBLE);

                getDeviceStatus(SSDPdevices.get(position));
            }
        });

        btnScanDevices = findViewById(R.id.devices_scan);
        btnScanDevices.setOnClickListener(new View.OnClickListener() {

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

    private void getDeviceStatus(AlexaLocalDevices device) {

        deviceHostAddress = device.getHostAddress();
        deviceName = device.getFriendlyName();
        Log.d(TAG, "Device host address : " + deviceHostAddress);
        this.transport = new SoftAPTransport(deviceHostAddress + ":80");
        this.security = new Security0();
        this.session = new Session(this.transport, this.security);

        this.session.sessionListener = new Session.SessionListener() {

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

        establishSession();
    }

    private void establishSession() {

        this.session.init(null);
        if (this.session.isEstablished()) {
            // Check signin status
            getStatus();
        }
    }

    private void getStatus() {

        Avsconfig.CmdSignInStatus configRequest = Avsconfig.CmdSignInStatus.newBuilder()
                .setDummy(123)
                .build();
        Avsconfig.AVSConfigMsgType msgType = Avsconfig.AVSConfigMsgType.TypeCmdSignInStatus;
        Avsconfig.AVSConfigPayload payload = Avsconfig.AVSConfigPayload.newBuilder()
                .setMsg(msgType)
                .setCmdSigninStatus(configRequest)
                .build();
        byte[] message = this.security.encrypt(payload.toByteArray());

        this.transport.sendConfigData(AVS_CONFIG_PATH, message, new ResponseListener() {

            @Override
            public void onSuccess(byte[] returnData) {

                Avsconfig.AVSConfigStatus deviceStatus = processSignInStatusResponse(returnData);
                Log.d(TAG, "SignIn Status Received : " + deviceStatus);
                finish();

                if (deviceStatus.equals(Avsconfig.AVSConfigStatus.SignedIn)) {

                    goToAlexaActivity();

                } else {
                    goToLoginActivity();
                }
            }

            @Override
            public void onFailure(Exception e) {
                Log.d(TAG, "Error in getting status");
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
            Log.d(TAG, "SignIn Status message " + status.getNumber() + status.toString());

        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return status;
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

        discoveryTask = new UPnPDiscovery(this, new UPnPDiscovery.OnDiscoveryListener() {

            @Override
            public void OnStart() {
                Log.d(TAG, "Starting discovery");
            }

            @Override
            public void OnFoundNewDevice(UPnPDevice device) {

//                Log.d("ScanLocalDevices", "Found  device: " + device.toString());
                final UPnPDevice foundDevice = device;

                runOnUiThread(new Runnable() {

                    public void run() {

                        boolean deviceExists = false;

                        for (AlexaLocalDevices alreadyHere : SSDPdevices) {

                            if (foundDevice.getHostAddress().equals(alreadyHere.getHostAddress())) {

                                deviceExists = true;
                                SSDPdevices.remove(alreadyHere);
                                SSDPadapter.remove(alreadyHere.getHostAddress() + " | " + alreadyHere.getFriendlyName());
                                SSDPadapter.notifyDataSetChanged();

                                Log.d(TAG, "Device already exists -" + foundDevice.getST());
                                syncAlexaUpNP(alreadyHere, foundDevice);

                                if (alreadyHere.getFriendlyName() != null) {

                                    SSDPdevices.add(alreadyHere);
                                    SSDPadapter.add(alreadyHere.getHostAddress() + " | " + alreadyHere.getFriendlyName());
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
                            SSDPadapter.add(foundAlexa.getHostAddress() + " | " + foundAlexa.getFriendlyName());
//                                                      SSDPadapter.notifyDataSetChanged();
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

        discoveryTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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

            btnScanDevices.setEnabled(false);
            btnScanDevices.setAlpha(0.5f);
            btnScanDevices.setTextColor(Color.WHITE);
            progressBar.setVisibility(View.VISIBLE);
            progressText.setVisibility(View.VISIBLE);
            deviceList.setVisibility(View.GONE);

        } else {

            btnScanDevices.setEnabled(true);
            btnScanDevices.setAlpha(1f);
            progressBar.setVisibility(View.GONE);
            progressText.setVisibility(View.GONE);
            deviceList.setVisibility(View.VISIBLE);
        }
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

    private Runnable runnable = new Runnable() {

        @Override
        public void run() {

            Log.e(TAG, "Timeout occurred.");
            discoveryTask.cancel(true);
            isScanning = false;
            updateProgressAndScanBtn();
        }
    };
}
