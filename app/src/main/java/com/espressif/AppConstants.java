package com.espressif;

public class AppConstants {

    // Constants for WiFi Security values (As per proto files)
    public static final short WIFI_OPEN = 0;
    public static final short WIFI_WEP = 1;
    public static final short WIFI_WPA_PSK = 2;
    public static final short WIFI_WPA2_PSK = 3;
    public static final short WIFI_WPA_WPA2_PSK = 4;
    public static final short WIFI_WPA2_ENTERPRISE = 5;

    // End point names
    public static final String HANDLER_PROV_SCAN = "prov-scan";
    public static final String HANDLER_PROTO_VER = "proto-ver";
    public static final String HANDLER_PROV_SESSION = "prov-session";
    public static final String HANDLER_CLOUD_USER_ASSOC = "cloud_user_assoc";

    // Keys used to pass data between activities and to store data in SharedPreference.
    public static final String KEY_WIFI_SECURITY_TYPE = "wifi_security";
    public static final String KEY_PROOF_OF_POSSESSION = "proof_of_possession";
    public static final String KEY_WIFI_NETWORK_NAME_PREFIX = "wifi_network_name_prefix";
    public static final String KEY_BLE_DEVICE_NAME_PREFIX = "ble_device_name_prefix";
    public static final String KEY_DEVICE_NAME = "device_name";
    public static final String KEY_STATUS_MSG = "status_msg";
    public static final String KEY_ESP_DEVICE = "esp_device";

    public static final String ESP_PREFERENCES = "Esp_Preferences";

    // UI Types of Device
    public static final String UI_TYPE_TOGGLE = "esp-ui-toggle";
    public static final String UI_TYPE_SLIDER = "esp-ui-slider";

    // ESP Device Types
    public static final String ESP_DEVICE_BULB = "esp.device.lightbulb";
    public static final String ESP_DEVICE_SWITCH = "esp.device.switch";

    public enum UpdateEventType {
        EVENT_DEVICE_ADDED,
        EVENT_ADD_DEVICE_TIME_OUT,
        EVENT_REMOVE
    }
}
