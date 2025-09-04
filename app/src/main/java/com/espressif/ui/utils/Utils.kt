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

package com.espressif.ui.utils

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.wifi_provisioning.R
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

object Utils {
    @JvmStatic
    fun byteArrayToDs(byteArray: ByteArray): String {
        val sb = StringBuilder()
        for (b in byteArray) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    fun displayDeviceConnectionError(aContext: Activity, msg: String?) {
        val builder = AlertDialog.Builder(aContext)
        builder.setCancelable(false)

        builder.setTitle(R.string.error_title)
        builder.setMessage(msg)

        // Set up the buttons
        builder.setPositiveButton(
            R.string.btn_ok
        ) { dialog, which ->
            val provisionManager = ESPProvisionManager.getInstance(aContext.applicationContext)
            if (provisionManager.espDevice != null) {
                provisionManager.espDevice.disconnectDevice()
            }
            dialog.dismiss()
            aContext.finish()
        }

        builder.show()
    }

    /**
     * Check the device to make sure it has the Google Play Services APK.
     *
     * @return Returns true if Google Api is available.
     */
    fun isPlayServicesAvailable(appContext: Context): Boolean {
        try {
            val apiAvailability = GoogleApiAvailability.getInstance()
            val resultCode = apiAvailability.isGooglePlayServicesAvailable(appContext)
            return resultCode == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
