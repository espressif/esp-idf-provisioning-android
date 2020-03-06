package com.espressif;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.espressif.provision.Provision;
import com.espressif.ui.models.EspNode;

import java.util.HashMap;

public class EspApplication extends Application {

    private static final String TAG = EspApplication.class.getSimpleName();

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

    public HashMap<String, EspNode> nodeMap;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ESP Application is created");
    }

    public void enableOnlyWifiNetwork(final Activity activity) {

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkRequest.Builder request = new NetworkRequest.Builder();
        request.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);

        networkCallback = new ConnectivityManager.NetworkCallback() {

            @Override
            public void onAvailable(Network network) {

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {

//                        connectivityManager.setProcessDefaultNetwork(network);

                    ConnectivityManager.setProcessDefaultNetwork(null);

                    if (fetchWifiSSID(activity) != null && fetchWifiSSID(activity).contains("PROV")) {
                        ConnectivityManager.setProcessDefaultNetwork(network);
                    } else {
                    }

                } else {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

//                            Log.e(TAG, "bindProcessToNetwork : " + connectivityManager.bindProcessToNetwork(network));
//                            connectivityManager.bindProcessToNetwork(network);

                        if (Build.VERSION.RELEASE.equalsIgnoreCase("6.0")) {

                            if (!Settings.System.canWrite(getApplicationContext())) {
                                Intent goToSettings = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                                goToSettings.setData(Uri.parse("package:" + getApplicationContext().getPackageName()));
                                getApplicationContext().startActivity(goToSettings);
                            }
                        }
                        connectivityManager.bindProcessToNetwork(null);

                        if (fetchWifiSSID(activity) != null && fetchWifiSSID(activity).contains("PROV")) {
                            connectivityManager.bindProcessToNetwork(network);
                        } else {
                            // TODO
                        }
                    }
                }
            }
        };

        connectivityManager.registerNetworkCallback(request.build(), networkCallback);
    }

    private String fetchWifiSSID(Activity activity) {

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
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, Provision.REQUEST_PERMISSIONS_CODE);
        }
        return ssid;
    }

    public void disableOnlyWifiNetwork() {

        Log.d(TAG, "disableOnlyWifiNetwork()");

        if (connectivityManager != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                ConnectivityManager.setProcessDefaultNetwork(null);
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    connectivityManager.bindProcessToNetwork(null);
                }
            }
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
    }
}
