// Copyright 2025 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif

object AppConstants {
    
    // Keys used to pass data between activities and to store data in SharedPreference.
    const val KEY_WIFI_SECURITY_TYPE: String = "wifi_security"
    const val KEY_PROOF_OF_POSSESSION: String = "proof_of_possession"
    const val KEY_WIFI_DEVICE_NAME_PREFIX: String = "wifi_network_name_prefix"
    const val KEY_BLE_DEVICE_NAME_PREFIX: String = "ble_device_name_prefix"
    const val KEY_DEVICE_NAME: String = "device_name"
    const val KEY_STATUS_MSG: String = "status_msg"
    const val KEY_WIFI_SSID: String = "ssid"
    const val KEY_WIFI_PASSWORD: String = "password"
    const val KEY_DEVICE_TYPES: String = "device_types"
    const val KEY_SECURITY_TYPE: String = "security_type"
    const val KEY_USER_NAME_WIFI: String = "sec2_username_wifi"
    const val KEY_USER_NAME_THREAD: String = "sec2_username_thread"
    const val KEY_THREAD_DATASET: String = "thread_dataset"
    const val KEY_THREAD_SCAN_AVAILABLE: String = "thread_scan_available"

    // In-app review
    const val KEY_REVIEW_PROV_SUCCESS_COUNT: String = "review_prov_success_count"
    const val KEY_REVIEW_LAST_PROMPT_TIME: String = "review_last_prompt_time"
    const val KEY_PREF_CATEGORY_FEEDBACK: String = "feedback_category"
    const val KEY_RATE_APP: String = "rate_app"

    // In-app update
    const val KEY_UPDATE_LAST_PROMPT_VERSION: String = "update_last_prompt_version"
    const val KEY_UPDATE_LAST_PROMPT_TIME: String = "update_last_prompt_time"
    const val KEY_UPDATE_ACCEPTED_VERSION: String = "update_accepted_version"

    const val ESP_PREFERENCES: String = "Esp_Preferences"

    const val DEVICE_TYPE_SOFTAP: String = "softap"
    const val DEVICE_TYPE_BLE: String = "ble"
    const val DEVICE_TYPE_BOTH: String = "both"
    const val DEVICE_TYPE_DEFAULT: String = DEVICE_TYPE_BOTH

    const val SEC_TYPE_0: Int = 0
    const val SEC_TYPE_1: Int = 1
    const val SEC_TYPE_2: Int = 2
    const val SEC_TYPE_DEFAULT: Int = SEC_TYPE_2
    const val DEFAULT_USER_NAME_WIFI: String = "wifiprov"
    const val DEFAULT_USER_NAME_THREAD: String = "threadprov"

    const val CAPABILITY_WIFI_SCAN: String = "wifi_scan"
    const val CAPABILITY_THREAD_SCAN: String = "thread_scan"
    const val CAPABILITY_THREAD_PROV: String = "thread_prov"
}
