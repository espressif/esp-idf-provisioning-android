// Copyright 2020 Espressif Systems (Shanghai) PTE LTD
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.espressif;

public class AppConstants {

    // Keys used to pass data between activities and to store data in SharedPreference.
    public static final String KEY_WIFI_SECURITY_TYPE = "wifi_security";
    public static final String KEY_PROOF_OF_POSSESSION = "proof_of_possession";
    public static final String KEY_WIFI_DEVICE_NAME_PREFIX = "wifi_network_name_prefix";
    public static final String KEY_BLE_DEVICE_NAME_PREFIX = "ble_device_name_prefix";
    public static final String KEY_DEVICE_NAME = "device_name";
    public static final String KEY_STATUS_MSG = "status_msg";
    public static final String KEY_WIFI_SSID = "ssid";
    public static final String KEY_WIFI_PASSWORD = "password";
    public static final String KEY_DEVICE_TYPES = "device_types";
    public static final String KEY_SECURITY_TYPE = "security_type";
    public static final String KEY_USER_NAME_WIFI = "sec2_username_wifi";
    public static final String KEY_USER_NAME_THREAD = "sec2_username_thread";
    public static final String KEY_THREAD_DATASET = "thread_dataset";
    public static final String KEY_THREAD_SCAN_AVAILABLE = "thread_scan_available";

    public static final String ESP_PREFERENCES = "Esp_Preferences";

    public static final String DEVICE_TYPE_SOFTAP = "softap";
    public static final String DEVICE_TYPE_BLE = "ble";
    public static final String DEVICE_TYPE_BOTH = "both";
    public static final String DEVICE_TYPE_DEFAULT = DEVICE_TYPE_BOTH;

    public static final int SEC_TYPE_0 = 0;
    public static final int SEC_TYPE_1 = 1;
    public static final int SEC_TYPE_2 = 2;
    public static final int SEC_TYPE_DEFAULT = SEC_TYPE_2;
    public static final String DEFAULT_USER_NAME_WIFI = "wifiprov";
    public static final String DEFAULT_USER_NAME_THREAD = "threadprov";

    public static final String CAPABILITY_WIFI_SCAN = "wifi_scan";
    public static final String CAPABILITY_THREAD_SCAN = "thread_scan";
    public static final String CAPABILITY_THREAD_PROV = "thread_prov";

    // Nombre de las preferencias compartidas
    public static final String PREF_NAME_USER = "user_preferences";
    
    // Claves para SharedPreferences
    public static final String KEY_IS_LOGGED_IN = "is_logged_in";
    public static final String KEY_USER_NAME = "user_name";
    public static final String KEY_USER_EMAIL = "user_email";
    public static final String KEY_USER_TYPE = "user_type";
    public static final String KEY_PATIENT_ID = "patient_id";
    public static final String KEY_CONNECTED_PATIENT_ID = "connected_patient_id";
    public static final String KEY_CONNECTED_PATIENT_EMAIL = "connected_patient_email";
    public static final String KEY_CONNECTED_PATIENT_NAME = "connected_patient_name";
    public static final String KEY_PROVISIONING_COMPLETED = "provisioning_completed";
    public static final String KEY_HAS_COMPLETED_PROVISIONING = "hasCompletedProvisioning";
    public static final String KEY_IS_PROVISIONED = "is_provisioned";
    public static final String KEY_CONNECTED_DEVICE_ID = "connected_device_id";
    // Flag para indicar si el onboarding está completo
    public static final String KEY_ONBOARDING_COMPLETED = "onboarding_completed";

    // Tipos de usuario
    public static final String USER_TYPE_PATIENT = "patient";
    public static final String USER_TYPE_FAMILY = "family";

    // MQTT constants
    public static final String MQTT_BROKER_URL = "ssl://broker.emqx.io:8883";
    public static final String MQTT_USER = "Wenerr";
    public static final String MQTT_PASSWORD = "Wenerr14";
    
    // Base topic structure
    public static final String MQTT_BASE_TOPIC = "mediwatch/%s";  // %s será reemplazado por PROV_XXXXXX
    
    // Topic templates
    public static final String MQTT_TOPIC_DEVICE_COMMANDS = MQTT_BASE_TOPIC + "/commands";
    public static final String MQTT_TOPIC_DEVICE_STATUS = MQTT_BASE_TOPIC + "/status";
    public static final String MQTT_TOPIC_DEVICE_TELEMETRY = MQTT_BASE_TOPIC + "/telemetry";
    public static final String MQTT_TOPIC_DEVICE_RESPONSE = MQTT_BASE_TOPIC + "/response";
    public static final String MQTT_TOPIC_DEVICE_CONFIRMATION = MQTT_BASE_TOPIC + "/med_confirmation";
    public static final String MQTT_TOPIC_DEVICE_TAKEN = MQTT_BASE_TOPIC + "/taken";
    public static final String MQTT_TOPIC_DEVICE_NAME = MQTT_BASE_TOPIC + "/name";
    
    public static final int MQTT_CONNECTION_TIMEOUT_MS = 5000;

    // Helper method to build topics
    public static String buildTopic(String topicTemplate, String deviceName) {
        return String.format(topicTemplate, deviceName);
    }
}
