package com.espressif.ui.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.espressif.AppConstants;
import com.espressif.provision.BuildConfig;
import com.espressif.provision.Provision;
import com.espressif.provision.R;
import com.espressif.provision.transport.ResponseListener;
import com.espressif.provision.transport.SoftAPTransport;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.wang.avi.AVLoadingIndicatorView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AddDeviceActivity extends AppCompatActivity {

    private static final String TAG = AddDeviceActivity.class.getSimpleName();
    private static final int REQUEST_CAMERA_PERMISSION = 201;

    public static String KEY_CAPTURED_BARCODE = "key_captured_barcode";
    public static String KEY_CAPTURED_RAW_BARCODE = "key_captured_raw_barcode";

    private CardView btnAddManually;
    private TextView txtAddManuallyBtn;
    private ImageView arrowImage;

    private SurfaceView surfaceView;
    private BarcodeDetector barcodeDetector;
    private CameraSource cameraSource;
    private AVLoadingIndicatorView loader;
    private Intent intent;
    private Handler handler;
    private String deviceName;
    private boolean isScanned = false;
    private int deviceConnectionReqCount = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_device);
        handler = new Handler();
        intent = new Intent();
        initViews();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraSource != null) {
            try {
                cameraSource.release();
            } catch (NullPointerException ignored) {
            }
            cameraSource = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        initialiseDetectorsAndSources();
    }

    @Override
    protected void onDestroy() {

        hideLoading();
        handler.removeCallbacks(fetchSSIDTask);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setResult(RESULT_CANCELED, intent);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION) {

            if (ActivityCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

                initialiseDetectorsAndSources();
                surfaceView.setVisibility(View.VISIBLE);
            }
        }
    }

    View.OnClickListener btnAddDeviceClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View v) {

            String BASE_URL = getResources().getString(R.string.wifi_base_url);
            String transportVersion, securityVersion;
            if (BuildConfig.FLAVOR_security.equals("sec1")) {
                securityVersion = Provision.CONFIG_SECURITY_SECURITY1;
            } else {
                securityVersion = Provision.CONFIG_SECURITY_SECURITY0;
            }

            if (BuildConfig.FLAVOR_transport.equals("ble")) {
                transportVersion = Provision.CONFIG_TRANSPORT_BLE;
            } else {
                transportVersion = Provision.CONFIG_TRANSPORT_WIFI;
            }

            HashMap<String, String> config = new HashMap<>();
            config.put(Provision.CONFIG_TRANSPORT_KEY, transportVersion);
            config.put(Provision.CONFIG_SECURITY_KEY, securityVersion);
            config.put(Provision.CONFIG_BASE_URL_KEY, BASE_URL);

            finish();
            Provision.showProvisioningUI(AddDeviceActivity.this, config);
        }
    };

    private void initViews() {

        surfaceView = findViewById(R.id.surfaceView);
        btnAddManually = findViewById(R.id.btn_add_device_manually);
        txtAddManuallyBtn = findViewById(R.id.text_btn);
        loader = findViewById(R.id.loader);
        arrowImage = findViewById(R.id.iv_arrow);
        arrowImage.setVisibility(View.GONE);

        txtAddManuallyBtn.setText(R.string.btn_no_qr_code);
        btnAddManually.setOnClickListener(btnAddDeviceClickListener);

        barcodeDetector = new BarcodeDetector.Builder(this)
                .setBarcodeFormats(Barcode.QR_CODE)
                .build();

        if (ActivityCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

            surfaceView.setVisibility(View.VISIBLE);
        } else {
            ActivityCompat.requestPermissions(AddDeviceActivity.this, new
                    String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private void initialiseDetectorsAndSources() {

        cameraSource = new CameraSource.Builder(this, barcodeDetector)
                .setRequestedPreviewSize(1920, 1080)
                .setAutoFocusEnabled(true) //you should add this feature
                .build();

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {

            @Override
            public void surfaceCreated(SurfaceHolder holder) {

                try {
                    if (ActivityCompat.checkSelfPermission(AddDeviceActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

                        if (cameraSource != null) {
                            cameraSource.start(surfaceView.getHolder());
                        }
                    } else {
                        ActivityCompat.requestPermissions(AddDeviceActivity.this, new
                                String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (cameraSource != null) {
                    cameraSource.stop();
                }
            }
        });

        barcodeDetector.setProcessor(new Detector.Processor<Barcode>() {

            @Override
            public void release() {
            }

            @Override
            public void receiveDetections(Detector.Detections<Barcode> detections) {

                final SparseArray<Barcode> barcodes = detections.getDetectedItems();

                if (barcodes.size() != 0 && !isScanned) {

                    Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                    vib.vibrate(50);

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            showLoading();
                        }
                    });

                    Log.d(TAG, "Barcodes size : " + barcodes.size());
                    Barcode barcode = barcodes.valueAt(0);
                    Log.d(TAG, "QR Code Data : " + barcode.rawValue);
                    String scannedData = barcode.rawValue;

                    intent.putExtra(KEY_CAPTURED_BARCODE, barcode);
                    intent.putExtra(KEY_CAPTURED_RAW_BARCODE, barcode.rawValue);
                    sendWiFiConnectionFailure();

                    try {
                        JSONObject jsonObject = new JSONObject(scannedData);
                        intent.putExtra(KEY_CAPTURED_RAW_BARCODE, barcode.rawValue);

                        deviceName = jsonObject.optString("name");
                        String pop = jsonObject.optString("pop");
                        String transport = jsonObject.optString("transport");
                        String password = jsonObject.optString("password");
                        isScanned = true;

                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(new Runnable() {

                            @Override
                            public void run() {
                                cameraSource.release();
                            }
                        });

                        // FIXME Currently considering SoftAP as transport and connect device.

                        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                        if (!wifiManager.isWifiEnabled()) {
                            wifiManager.setWifiEnabled(true);
                        }

                        Log.d(TAG, "Device name : " + deviceName);
                        Log.d(TAG, "Device password : " + password);

                        WifiConfiguration config = new WifiConfiguration();
                        config.SSID = String.format("\"%s\"", deviceName);

                        if (TextUtils.isEmpty(password)) {
                            Log.i(TAG, "Connect to open network");
                            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                        } else {
                            Log.i(TAG, "Connect to secure network");
                            config.preSharedKey = String.format("\"%s\"", password);
                        }

                        int netId = -1;
                        List<WifiConfiguration> apList = wifiManager.getConfiguredNetworks();
                        Log.e(TAG, "List Size : " + apList.size());

                        for (WifiConfiguration i : apList) {

                            if (i.SSID != null && i.SSID.equals("\"" + deviceName + "\"")) {
                                Log.e(TAG, "i.networkId : " + i.networkId);
//                                wifiManager.removeNetwork(i.networkId);
                                netId = i.networkId;
                            }
                        }

                        if (netId == -1) {

                            netId = wifiManager.addNetwork(config);
                            Log.e(TAG, "Network Id : " + netId);
                        }

                        if (netId != -1) {

                            Log.e(TAG, "Enable network : " + netId);
//                            wifiManager.disconnect();
                            wifiManager.enableNetwork(netId, true);
//                            wifiManager.reconnect();

                        } else {

                            List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();

                            for (WifiConfiguration i : list) {

                                if (i.SSID != null && i.SSID.equals("\"" + deviceName + "\"")) {
                                    Log.e(TAG, "i.networkId 2 : " + i.networkId);
                                    wifiManager.removeNetwork(i.networkId);
                                    break;
                                }
                            }

                            netId = wifiManager.addNetwork(config);
                            wifiManager.disconnect();
                            wifiManager.enableNetwork(netId, true);
                            wifiManager.reconnect();
                        }

                        intent.putExtra("deviceName", deviceName);
                        intent.putExtra("pop", pop);
                        intent.putExtra("transport", transport);
                        intent.putExtra("password", password);
                        checkDeviceConnection();

                    } catch (JSONException e) {

                        e.printStackTrace();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                intent.putExtra("err_msg", "Error! QR code is not valid. Please try again.");
                                setResult(RESULT_CANCELED, intent);
                                finish();
                            }
                        });
                    }
                }
            }
        });
    }

    private void checkDeviceConnection() {
        handler.postDelayed(fetchSSIDTask, 2000);
    }

    private void sendWiFiConnectionFailure() {
        handler.postDelayed(wifiConnectionFailedTask, 10000);
    }

    private void connectWithDevice() {
        handler.removeCallbacks(connectWithDeviceTask);
        handler.postDelayed(connectWithDeviceTask, 100);
    }

    private void sendDeviceConnectionFailure() {
        handler.postDelayed(deviceConnectionFailedTask, 100);
    }

    private String fetchWifiSSID() {

        String ssid = null;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
            if (networkInfo == null) {
                return null;
            }

            if (networkInfo.isConnected()) {
                final WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
                if (connectionInfo != null) {
                    ssid = connectionInfo.getSSID();
                    ssid = ssid.replaceAll("^\"|\"$", "");
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 100);
        }
        return ssid;
    }

    private Runnable fetchSSIDTask = new Runnable() {

        @Override
        public void run() {

            String ssid = fetchWifiSSID();
            Log.d(TAG, "Fetch SSID : " + ssid);
            Log.d(TAG, "deviceName : " + deviceName);

            if (ssid != null && ssid.startsWith(deviceName)) {

                Log.d(TAG, "Send Result + " + RESULT_OK);

                try {
                    Thread.sleep(2500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                handler.removeCallbacks(wifiConnectionFailedTask);
                connectWithDevice();
//                setResult(RESULT_OK, intent);
//                finish();
            } else {

                handler.removeCallbacks(fetchSSIDTask);
                checkDeviceConnection();
            }
        }
    };

    private Runnable wifiConnectionFailedTask = new Runnable() {

        @Override
        public void run() {

            handler.removeCallbacks(fetchSSIDTask);
            intent.putExtra("err_msg", "Error! Failed to connect with device.");
            setResult(RESULT_CANCELED, intent);
            finish();
        }
    };

    private Runnable deviceConnectionFailedTask = new Runnable() {

        @Override
        public void run() {

            handler.removeCallbacks(connectWithDeviceTask);
            intent.putExtra("err_msg", "Error! Unable to communicate with device.");
            setResult(RESULT_CANCELED, intent);
            finish();
        }
    };

    private void showLoading() {
        loader.setVisibility(View.VISIBLE);
        loader.show();
    }

    private void hideLoading() {
        loader.hide();
    }

    private Runnable connectWithDeviceTask = new Runnable() {

        @Override
        public void run() {

            if (intent != null) {

                deviceConnectionReqCount++;

                Log.e(TAG, "Data is not null");
                Log.e(TAG, "Start connecting with device ===================== " + System.currentTimeMillis());
                final String deviceName = intent.getStringExtra("deviceName");
                String password = intent.getStringExtra("password");
                final String pop = intent.getStringExtra("pop");
                String transportValue = intent.getStringExtra("transport");
                String BASE_URL = getResources().getString(R.string.wifi_base_url);

                Log.e(TAG, "deviceName : " + deviceName + " password : " + password + " pop : " + pop + " transportValue : " + transportValue);
                ProvisionLanding.softAPTransport = new SoftAPTransport(BASE_URL);
                String tempData = "ESP";

                ProvisionLanding.softAPTransport.sendConfigData(AppConstants.HANDLER_PROTO_VER, tempData.getBytes(), new ResponseListener() {

                    @Override
                    public void onSuccess(byte[] returnData) {

                        String data = new String(returnData, StandardCharsets.UTF_8);
                        Log.d(TAG, "Value : " + data);

                        try {
                            JSONObject jsonObject = new JSONObject(data);
                            JSONObject provInfo = jsonObject.getJSONObject("prov");

                            String versionInfo = provInfo.getString("ver");
                            Log.d(TAG, "Device Version : " + versionInfo);

                            JSONArray capabilities = provInfo.getJSONArray("cap");
                            ProvisionLanding.deviceCapabilities = new ArrayList<>();

                            for (int i = 0; i < capabilities.length(); i++) {
                                String cap = capabilities.getString(i);
                                ProvisionLanding.deviceCapabilities.add(cap);
                            }
                            Log.d(TAG, "Capabilities : " + ProvisionLanding.deviceCapabilities);

                            handler.removeCallbacks(connectWithDeviceTask);
                            setResult(RESULT_OK, intent);
                            finish();

                        } catch (JSONException e) {

                            e.printStackTrace();

                            Log.d(TAG, "Capabilities JSON not available.");
                            handler.removeCallbacks(connectWithDeviceTask);
                            setResult(RESULT_OK, intent);
                            finish();
                        }
                    }

                    @Override
                    public void onFailure(Exception e) {

                        e.printStackTrace();

                        if (deviceConnectionReqCount == 3) {

                            runOnUiThread(new Runnable() {

                                @Override
                                public void run() {
                                    hideLoading();
                                    handler.removeCallbacks(connectWithDeviceTask);
                                    sendDeviceConnectionFailure();
                                }
                            });
                        } else {
                            handler.removeCallbacks(connectWithDeviceTask);
                            handler.postDelayed(connectWithDeviceTask, 2000);
                        }
                    }
                });
            }
        }
    };
}
