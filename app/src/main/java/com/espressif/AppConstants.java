package com.espressif;

public class AppConstants {

    // Constants for WiFi Security values (As per proto files)
    public static final short WIFI_OPEN = 0;
    public static final short WIFI_WEP = 1;
    public static final short WIFI_WPA_PSK = 2;
    public static final short WIFI_WPA2_PSK = 3;
    public static final short WIFI_WPA_WPA2_PSK = 4;
    public static final short WIFI_WPA2_ENTERPRISE = 5;

    // Keys used to pass data between activities and to store data in SharedPreference.
    public static final String KEY_PROOF_OF_POSSESSION = "proof_of_possession";
    public static final String KEY_WIFI_SECURITY_TYPE = "wifi_security";
}
