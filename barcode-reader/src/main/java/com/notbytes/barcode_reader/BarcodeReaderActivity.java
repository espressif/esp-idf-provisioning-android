package com.notbytes.barcode_reader;

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
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.gms.vision.barcode.Barcode;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class BarcodeReaderActivity extends AppCompatActivity implements BarcodeReaderFragment.BarcodeReaderListener {

    private static final String TAG = "BarcodeReader";

    public static String KEY_CAPTURED_BARCODE = "key_captured_barcode";
    public static String KEY_CAPTURED_RAW_BARCODE = "key_captured_raw_barcode";

    private static final String KEY_AUTO_FOCUS = "key_auto_focus";
    private static final String KEY_USE_FLASH = "key_use_flash";
    private boolean autoFocus = false;
    private boolean useFlash = false;
    private BarcodeReaderFragment mBarcodeReaderFragment;
    private String deviceName;
    private Intent intent;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_reader);

        final Intent intent = getIntent();
        if (intent != null) {
            autoFocus = intent.getBooleanExtra(KEY_AUTO_FOCUS, false);
            useFlash = intent.getBooleanExtra(KEY_USE_FLASH, false);
        }
        mBarcodeReaderFragment = attachBarcodeReaderFragment();
        handler = new Handler();
    }

    @Override
    protected void onDestroy() {

        if (mBarcodeReaderFragment != null) {
            mBarcodeReaderFragment.hideLoading();
        }
        handler.removeCallbacks(fetchSSIDTask);
        super.onDestroy();
    }

    private BarcodeReaderFragment attachBarcodeReaderFragment() {
        final FragmentManager supportFragmentManager = getSupportFragmentManager();
        final FragmentTransaction fragmentTransaction = supportFragmentManager.beginTransaction();
        BarcodeReaderFragment fragment = BarcodeReaderFragment.newInstance(autoFocus, useFlash);
        fragment.setListener(this);
        fragmentTransaction.replace(R.id.fm_container, fragment);
        fragmentTransaction.commitAllowingStateLoss();
        return fragment;
    }

    public static Intent getLaunchIntent(Context context, boolean autoFocus, boolean useFlash) {
        Intent intent = new Intent(context, BarcodeReaderActivity.class);
        intent.putExtra(KEY_AUTO_FOCUS, autoFocus);
        intent.putExtra(KEY_USE_FLASH, useFlash);
        return intent;
    }

    @Override
    public void onScanned(Barcode barcode) {

        if (mBarcodeReaderFragment != null) {
            mBarcodeReaderFragment.pauseScanning();
            mBarcodeReaderFragment.showLoading();
        }
        if (barcode != null) {

            intent = new Intent();
            intent.putExtra(KEY_CAPTURED_BARCODE, barcode);
            intent.putExtra(KEY_CAPTURED_RAW_BARCODE, barcode.rawValue);
            sendConnectionFailure();

            String scannedData = barcode.rawValue;

            try {
                JSONObject jsonObject = new JSONObject(scannedData);
                intent.putExtra(KEY_CAPTURED_RAW_BARCODE, barcode.rawValue);

                deviceName = jsonObject.optString("name");
                String pop = jsonObject.optString("pop");
                String transport = jsonObject.optString("transport");
                String password = jsonObject.optString("password");

                // FIXME Currently considering SoftAP as transport and connect device.
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        Toast.makeText(BarcodeReaderActivity.this, "Connecting with device", Toast.LENGTH_SHORT).show();
//                    }
//                });

                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                if (!wifiManager.isWifiEnabled()) {
                    wifiManager.setWifiEnabled(true);
                }

                Log.e(TAG, "Device name : " + deviceName);
                Log.e(TAG, "Device password : " + password);

                WifiConfiguration config = new WifiConfiguration();
                config.SSID = String.format("\"%s\"", deviceName);

                if (TextUtils.isEmpty(password)) {
                    Log.e(TAG, "Connect to open network");
                    config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                } else {
                    Log.e(TAG, "Connect to secure network");
                    config.preSharedKey = String.format("\"%s\"", password);
                }

                List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
                Log.e(TAG, "List Size : " + configuredNetworks.size());

                List<WifiConfiguration> apList = wifiManager.getConfiguredNetworks();
                Log.e(TAG, "List Size : " + apList.size());

                for (WifiConfiguration i : apList) {

                    Log.e(TAG, "SSID : " + i.SSID + " , Network Id : " + i.networkId);

                    if (i.SSID != null && i.SSID.equals("\"" + deviceName + "\"")) {
                        Log.e(TAG, "i.networkId : " + i.networkId);
                        wifiManager.removeNetwork(i.networkId);
                    }
                }

                int netId = wifiManager.addNetwork(config);
                Log.e(TAG, "Network Id : " + netId);

                if (netId != -1) {
                    wifiManager.disconnect();
                    wifiManager.enableNetwork(netId, true);
                    wifiManager.reconnect();

                } else {

                    List<WifiConfiguration> list = wifiManager.getConfiguredNetworks();
                    Log.e(TAG, "List Size : " + list.size());

                    for (WifiConfiguration i : list) {

                        Log.e(TAG, "SSID : " + i.SSID + " , Network Id : " + i.networkId);

                        if (i.SSID != null && i.SSID.equals("\"" + deviceName + "\"")) {
                            Log.e(TAG, "i.networkId : " + i.networkId);
                            wifiManager.disconnect();
                            wifiManager.enableNetwork(i.networkId, true);
                            wifiManager.reconnect();
                            break;
                        }
                    }
                }

                intent.putExtra("deviceName", deviceName);
                intent.putExtra("pop", pop);
                intent.putExtra("transport", transport);
                intent.putExtra("password", password);
                checkDeviceConnection();

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onScannedMultiple(List<Barcode> barcodes) {

    }

    @Override
    public void onBitmapScanned(SparseArray<Barcode> sparseArray) {

    }

    @Override
    public void onScanError(String errorMessage) {

    }

    @Override
    public void onCameraPermissionDenied() {

    }

    private void checkDeviceConnection() {
        handler.postDelayed(fetchSSIDTask, 2000);
    }

    private void sendConnectionFailure() {
        handler.postDelayed(connectionFailedTask, 10000);
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
            Log.e(TAG, "Fetch SSID : " + ssid);

            if (ssid != null && ssid.startsWith(deviceName)) {

                Log.e(TAG, "Send Result + " + RESULT_OK);

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                handler.removeCallbacks(connectionFailedTask);
                setResult(RESULT_OK, intent);
                finish();
            } else {

                handler.removeCallbacks(fetchSSIDTask);
                checkDeviceConnection();
            }
        }
    };

    private Runnable connectionFailedTask = new Runnable() {

        @Override
        public void run() {

            handler.removeCallbacks(fetchSSIDTask);
            setResult(RESULT_CANCELED, intent);
            finish();
        }
    };
}
