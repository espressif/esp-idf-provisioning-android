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

package com.espressif.ui.activities

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.espressif.AppConstants
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.ui.utils.Utils
import com.espressif.wifi_provisioning.R
import org.greenrobot.eventbus.EventBus
import org.json.JSONException
import org.json.JSONObject

open class ManualProvBaseActivity : AppCompatActivity() {

    companion object {
        private val TAG: String = ManualProvBaseActivity::class.java.simpleName
        private const val KEY_PRESET_USER_NAME = "preset_user_name"
    }

    @JvmField
    var securityType: Int = 0

    @JvmField
    var provisionManager: ESPProvisionManager? = null

    @JvmField
    var sharedPreferences: SharedPreferences? = null

    /**
     * Sec2 username that was already set on the device before this screen opened (for example
     * from a QR code). Empty in the manual flow, where the default for the device type is used.
     */
    private var presetUserName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_provision_landing)
        securityType =
            intent.getIntExtra(AppConstants.KEY_SECURITY_TYPE, AppConstants.SEC_TYPE_DEFAULT)
        provisionManager = ESPProvisionManager.getInstance(applicationContext)
        sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, MODE_PRIVATE)
        presetUserName = savedInstanceState?.getString(KEY_PRESET_USER_NAME)
            ?: (provisionManager!!.espDevice?.userName ?: "")
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        EventBus.getDefault().register(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PRESET_USER_NAME, presetUserName)
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            cleanUpOnBack()
            finish()
        }
    }

    /**
     * Called before the activity finishes on back navigation.
     * Subclasses overriding this must call super so the device is disconnected.
     */
    protected open fun cleanUpOnBack() {
        if (provisionManager!!.espDevice != null) {
            provisionManager!!.espDevice.disconnectDevice()
        }
    }

    fun setSecurityTypeFromVersionInfo() {
        val protoVerStr = provisionManager!!.espDevice.versionInfo

        try {
            val jsonObject = JSONObject(protoVerStr)
            val provInfo = jsonObject.getJSONObject("prov")

            if (provInfo != null) {
                if (provInfo.has("sec_ver")) {
                    val serVer = provInfo.optInt("sec_ver")
                    Log.d(TAG, "Security Version : $serVer")

                    when (serVer) {
                        AppConstants.SEC_TYPE_0 -> {
                            securityType = AppConstants.SEC_TYPE_0
                            provisionManager!!.espDevice.securityType =
                                ESPConstants.SecurityType.SECURITY_0
                        }

                        AppConstants.SEC_TYPE_1 -> {
                            securityType = AppConstants.SEC_TYPE_1
                            provisionManager!!.espDevice.securityType =
                                ESPConstants.SecurityType.SECURITY_1
                        }

                        else -> {
                            // Sec2, or a newer version unknown to this app: treat it as Sec2.
                            securityType = AppConstants.SEC_TYPE_2
                            provisionManager!!.espDevice.securityType =
                                ESPConstants.SecurityType.SECURITY_2
                            setSec2UserName()
                        }
                    }
                } else {
                    if (securityType == AppConstants.SEC_TYPE_2) {
                        securityType = AppConstants.SEC_TYPE_1
                        provisionManager!!.espDevice.securityType =
                            ESPConstants.SecurityType.SECURITY_1
                    }
                }
            } else {
                Log.e(TAG, "proto-ver info is not available.")
            }
        } catch (e: JSONException) {
            e.printStackTrace()
            Log.d(TAG, "Capabilities JSON not available.")
        }
    }

    /**
     * Applies the Sec2 username for the connected device: the one supplied before this screen
     * (for example from a QR code) if any, otherwise the default for the device type. Evaluated on
     * every connection, so reconnecting to a different kind of device from this screen picks the
     * matching default.
     */
    private fun setSec2UserName() {
        val espDevice = provisionManager!!.espDevice
        espDevice.userName = presetUserName.ifEmpty {
            Utils.getDefaultSec2UserName(espDevice.deviceCapabilities)
        }
    }
}
