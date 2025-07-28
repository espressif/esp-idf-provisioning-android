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

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import com.espressif.AppConstants
import com.espressif.provisioning.DeviceConnectionEvent
import com.espressif.provisioning.ESPConstants
import com.espressif.ui.utils.Utils
import com.espressif.wifi_provisioning.R
import com.espressif.wifi_provisioning.databinding.ActivityProvisionLandingBinding
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ProvisionLanding : ManualProvBaseActivity() {

    companion object {
        private val TAG: String = ProvisionLanding::class.java.simpleName

        private const val REQUEST_FINE_LOCATION = 10
        private const val WIFI_SETTINGS_ACTIVITY_REQUEST = 11
    }

    private lateinit var binding: ActivityProvisionLandingBinding

    private var deviceName: String? = null
    private var pop: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProvisionLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        deviceName = intent.getStringExtra(AppConstants.KEY_DEVICE_NAME)
        pop = intent.getStringExtra(AppConstants.KEY_PROOF_OF_POSSESSION)
        initViews()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            WIFI_SETTINGS_ACTIVITY_REQUEST -> if (hasPermissions()) {
                connectDevice()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_FINE_LOCATION -> {}
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: DeviceConnectionEvent) {
        Log.d(TAG, "On Device Prov Event RECEIVED : " + event.eventType)

        when (event.eventType) {
            ESPConstants.EVENT_DEVICE_CONNECTED -> {
                Log.d(TAG, "Device Connected Event Received")
                binding.btnConnect.layoutBtn.isEnabled = true
                binding.btnConnect.layoutBtn.alpha = 1f
                binding.btnConnect.textBtn.setText(R.string.btn_connect)
                binding.btnConnect.progressIndicator.visibility = View.GONE
                binding.btnConnect.ivArrow.visibility = View.VISIBLE
                setSecurityTypeFromVersionInfo()
                val isSecure = sharedPreferences!!.getBoolean(AppConstants.KEY_SECURITY_TYPE, true)
                if (isSecure) {
                    if (securityType == AppConstants.SEC_TYPE_0) {
                        Utils.displayDeviceConnectionError(
                            this,
                            getString(R.string.error_security_mismatch)
                        )
                    } else {
                        processDeviceCapabilities()
                    }
                } else {
                    if (securityType != AppConstants.SEC_TYPE_0) {
                        Utils.displayDeviceConnectionError(
                            this,
                            getString(R.string.error_security_mismatch)
                        )
                    } else {
                        processDeviceCapabilities()
                    }
                }
            }

            ESPConstants.EVENT_DEVICE_CONNECTION_FAILED -> {
                binding.btnConnect.layoutBtn.isEnabled = true
                binding.btnConnect.layoutBtn.alpha = 1f
                binding.btnConnect.textBtn.setText(R.string.btn_connect)
                binding.btnConnect.progressIndicator.visibility = View.GONE
                binding.btnConnect.ivArrow.visibility = View.VISIBLE
                Utils.displayDeviceConnectionError(
                    this,
                    getString(R.string.error_device_connect_failed)
                )
            }
        }
    }

    var btnConnectClickListener: View.OnClickListener = View.OnClickListener {
        startActivityForResult(
            Intent(Settings.ACTION_WIFI_SETTINGS),
            WIFI_SETTINGS_ACTIVITY_REQUEST
        )
    }

    private fun connectDevice() {
        binding.btnConnect.layoutBtn.isEnabled = false
        binding.btnConnect.layoutBtn.alpha = 0.5f
        binding.btnConnect.textBtn.setText(R.string.btn_connecting)
        binding.btnConnect.progressIndicator.visibility = View.VISIBLE
        binding.btnConnect.ivArrow.visibility = View.GONE

        if (ActivityCompat.checkSelfPermission(
                this@ProvisionLanding,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            provisionManager!!.espDevice.connectWiFiDevice()
        } else {
            Log.e(TAG, "Not able to connect device as Location permission is not granted.")
            Toast.makeText(
                this@ProvisionLanding,
                "Please give location permission to connect device",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun initViews() {
        setToolbar()
        var instruction = getString(R.string.connect_device_instruction_general)
        
        if (TextUtils.isEmpty(deviceName)) {
            binding.tvConnectDeviceInstruction.text = instruction
            binding.tvDeviceName.visibility = View.GONE
        } else {
            instruction = getString(R.string.connect_device_instruction_specific)
            binding.tvConnectDeviceInstruction.text = instruction
            binding.tvDeviceName.visibility = View.VISIBLE
            binding.tvDeviceName.text = deviceName
        }

        binding.btnConnect.textBtn.setText(R.string.btn_connect)
        binding.btnConnect.layoutBtn.setOnClickListener(btnConnectClickListener)
        hasPermissions()
    }

    private fun setToolbar() {
        setSupportActionBar(binding.titleBar.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        supportActionBar!!.setTitle(R.string.title_activity_connect_device)
        binding.titleBar.toolbar.navigationIcon =
            AppCompatResources.getDrawable(this, R.drawable.ic_arrow_left)
        binding.titleBar.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun processDeviceCapabilities() {
        val deviceCaps = provisionManager!!.espDevice.deviceCapabilities

        if (deviceCaps != null) {
            if (!TextUtils.isEmpty(pop)) {
                provisionManager!!.espDevice.proofOfPossession = pop

                if (deviceCaps.contains(AppConstants.CAPABILITY_WIFI_SCAN)) {
                    goToWifiScanListActivity()
                } else if (deviceCaps.contains(AppConstants.CAPABILITY_THREAD_SCAN)) {
                    goToThreadScanActivity(true)
                } else if (deviceCaps.contains(AppConstants.CAPABILITY_THREAD_PROV)) {
                    goToThreadScanActivity(false)
                } else {
                    goToWiFiConfigActivity()
                }
            } else {
                if (!deviceCaps.contains("no_pop") && securityType != AppConstants.SEC_TYPE_0) {
                    goToPopActivity()
                } else if (deviceCaps.contains(AppConstants.CAPABILITY_WIFI_SCAN)) {
                    goToWifiScanListActivity()
                } else if (deviceCaps.contains(AppConstants.CAPABILITY_THREAD_SCAN)) {
                    goToThreadScanActivity(true)
                } else if (deviceCaps.contains(AppConstants.CAPABILITY_THREAD_PROV)) {
                    goToThreadScanActivity(false)
                } else {
                    goToWiFiConfigActivity()
                }
            }
        } else {
            goToWiFiConfigActivity()
        }
    }

    private fun goToPopActivity() {
        finish()
        val popIntent = Intent(applicationContext, ProofOfPossessionActivity::class.java)
        startActivity(popIntent)
    }

    private fun goToWifiScanListActivity() {
        finish()
        val wifiListIntent = Intent(applicationContext, WiFiScanActivity::class.java)
        startActivity(wifiListIntent)
    }

    private fun goToThreadScanActivity(scanCapAvailable: Boolean) {
        finish()
        val threadConfigIntent = Intent(applicationContext, ThreadConfigActivity::class.java)
        threadConfigIntent.putExtra(AppConstants.KEY_THREAD_SCAN_AVAILABLE, scanCapAvailable)
        startActivity(threadConfigIntent)
    }

    private fun goToWiFiConfigActivity() {
        finish()
        val wifiConfigIntent = Intent(applicationContext, WiFiConfigActivity::class.java)
        startActivity(wifiConfigIntent)
    }

    private fun hasPermissions(): Boolean {
        if (!hasLocationPermissions()) {
            requestLocationPermission()
            return false
        }
        return true
    }

    private fun hasLocationPermissions(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_FINE_LOCATION)
    }
}
