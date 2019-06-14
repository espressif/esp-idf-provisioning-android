package com.espressif.ui;

public class AppConstants {

    // Constants for WiFi Security values (As per proto files)
    public static final short WIFI_OPEN = 0;
    public static final short WIFI_WEP = 1;
    public static final short WIFI_WPA_PSK = 2;
    public static final short WIFI_WPA2_PSK = 3;
    public static final short WIFI_WPA_WPA2_PSK = 4;
    public static final short WIFI_WPA2_ENTERPRISE = 5;

    // Request codes
    public static final int REQUEST_ENABLE_BT = 101;
    public static final int REQUEST_FINE_LOCATION = 102;

    // End point names
    public static final String HANDLER_PROV_SCAN = "prov-scan";
    public static final String HANDLER_PROTO_VER = "proto-ver";
    public static final String HANDLER_PROV_SESSION = "prov-session";

    // Keys used to pass data between activities and to store data in SharedPreference.
    public static final String KEY_WIFI_SECURITY_TYPE = "wifi_security";
    public static final String KEY_PROOF_OF_POSSESSION = "proof_of_possession";
}
