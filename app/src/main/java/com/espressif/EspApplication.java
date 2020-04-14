package com.espressif;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

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

                if (Build.VERSION.RELEASE.equalsIgnoreCase("6.0")) {

                    if (!Settings.System.canWrite(getApplicationContext())) {
                        Intent goToSettings = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                        goToSettings.setData(Uri.parse("package:" + getApplicationContext().getPackageName()));
                        getApplicationContext().startActivity(goToSettings);
                    }
                }
                connectivityManager.bindProcessToNetwork(network);
            }
        };
        connectivityManager.registerNetworkCallback(request.build(), networkCallback);
    }

    public void disableOnlyWifiNetwork() {

        Log.d(TAG, "disableOnlyWifiNetwork()");

        if (connectivityManager != null) {

            try {
                connectivityManager.bindProcessToNetwork(null);
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception e) {
                Log.e(TAG, "Connectivity Manager is already unregistered");
            }
        }
    }
}
