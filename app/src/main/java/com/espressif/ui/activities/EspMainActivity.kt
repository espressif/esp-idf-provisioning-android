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

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.espressif.AppConstants
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.wifi_provisioning.BuildConfig
import com.espressif.wifi_provisioning.R
import com.espressif.wifi_provisioning.databinding.ActivityEspMainBinding

class EspMainActivity : AppCompatActivity() {

    companion object {
        private val TAG: String = EspMainActivity::class.java.simpleName

        // Request codes
        private const val REQUEST_LOCATION = 1
        private const val REQUEST_ENABLE_BT = 2
    }

    private lateinit var binding: ActivityEspMainBinding

    private var provisionManager: ESPProvisionManager? = null
    private var ivEsp: ImageView? = null
    private var sharedPreferences: SharedPreferences? = null
    private var deviceType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEspMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        initViews()

        sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, MODE_PRIVATE)
        provisionManager = ESPProvisionManager.getInstance(applicationContext)
    }

    override fun onResume() {
        super.onResume()

        deviceType = sharedPreferences!!.getString(
            AppConstants.KEY_DEVICE_TYPES,
            AppConstants.DEVICE_TYPE_DEFAULT
        )
        if (deviceType == "wifi") {
            val editor = sharedPreferences!!.edit()
            editor.putString(AppConstants.KEY_DEVICE_TYPES, AppConstants.DEVICE_TYPE_DEFAULT)
            editor.apply()
        }

        deviceType = sharedPreferences!!.getString(
            AppConstants.KEY_DEVICE_TYPES,
            AppConstants.DEVICE_TYPE_DEFAULT
        )
        if (deviceType == AppConstants.DEVICE_TYPE_BLE) {
            ivEsp!!.setImageResource(R.drawable.ic_esp_ble)
        } else if (deviceType == AppConstants.DEVICE_TYPE_SOFTAP) {
            ivEsp!!.setImageResource(R.drawable.ic_esp_softap)
        } else {
            ivEsp!!.setImageResource(R.drawable.ic_esp)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (BuildConfig.isSettingsAllowed) {
            // Inflate the menu; this adds items to the action bar if it is present.
            menuInflater.inflate(R.menu.menu_settings, menu)
            return true
        } else {
            menu.clear()
            return true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId

        if (id == R.id.action_settings) {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_LOCATION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (isLocationEnabled) {
                    addDeviceClick()
                }
            }
        }

        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            Toast.makeText(
                this,
                "Bluetooth is turned ON, you can provision device now.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun initViews() {
        ivEsp = findViewById(R.id.iv_esp)
        binding.layoutEspMain.btnProvisionDevice.ivArrow.visibility = View.GONE
        binding.layoutEspMain.btnProvisionDevice.layoutBtn.setOnClickListener(
            addDeviceBtnClickListener
        )

        val tvAppVersion = findViewById<TextView>(R.id.tv_app_version)

        var version = ""
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            version = pInfo.versionName.toString()
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        val appVersion = getString(R.string.app_version) + " - v" + version
        tvAppVersion.text = appVersion
    }

    private var addDeviceBtnClickListener: View.OnClickListener = object : View.OnClickListener {
        override fun onClick(v: View) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (!isLocationEnabled) {
                    askForLocation()
                    return
                }
            }
            addDeviceClick()
        }
    }

    private fun addDeviceClick() {
        if (BuildConfig.isQrCodeSupported) {
            gotoQrCodeActivity()
        } else {
            if (deviceType == AppConstants.DEVICE_TYPE_BLE || deviceType == AppConstants.DEVICE_TYPE_BOTH) {
                val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
                val bleAdapter = bluetoothManager.adapter

                if (!bleAdapter.isEnabled) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                } else {
                    startProvisioningFlow()
                }
            } else {
                startProvisioningFlow()
            }
        }
    }

    private fun startProvisioningFlow() {
        deviceType = sharedPreferences!!.getString(
            AppConstants.KEY_DEVICE_TYPES,
            AppConstants.DEVICE_TYPE_DEFAULT
        )
        val isSec1 = sharedPreferences!!.getBoolean(AppConstants.KEY_SECURITY_TYPE, true)
        Log.d(TAG, "Device Types : $deviceType")
        Log.d(TAG, "isSec1 : $isSec1")
        var securityType = 0
        if (isSec1) {
            securityType = 1
        }

        if (deviceType == AppConstants.DEVICE_TYPE_BLE) {
            if (isSec1) {
                provisionManager!!.createESPDevice(
                    ESPConstants.TransportType.TRANSPORT_BLE,
                    ESPConstants.SecurityType.SECURITY_1
                )
            } else {
                provisionManager!!.createESPDevice(
                    ESPConstants.TransportType.TRANSPORT_BLE,
                    ESPConstants.SecurityType.SECURITY_0
                )
            }
            goToBLEProvisionLandingActivity(securityType)
        } else if (deviceType == AppConstants.DEVICE_TYPE_SOFTAP) {
            if (isSec1) {
                provisionManager!!.createESPDevice(
                    ESPConstants.TransportType.TRANSPORT_SOFTAP,
                    ESPConstants.SecurityType.SECURITY_1
                )
            } else {
                provisionManager!!.createESPDevice(
                    ESPConstants.TransportType.TRANSPORT_SOFTAP,
                    ESPConstants.SecurityType.SECURITY_0
                )
            }
            goToWiFiProvisionLandingActivity(securityType)
        } else {
            val deviceTypes = arrayOf("BLE", "SoftAP")
            val builder = AlertDialog.Builder(this)
            builder.setCancelable(true)
            builder.setTitle(R.string.dialog_msg_device_selection)
            val finalSecurityType = securityType
            builder.setItems(
                deviceTypes
            ) { dialog, position ->
                when (position) {
                    0 -> {
                        if (isSec1) {
                            provisionManager!!.createESPDevice(
                                ESPConstants.TransportType.TRANSPORT_BLE,
                                ESPConstants.SecurityType.SECURITY_1
                            )
                        } else {
                            provisionManager!!.createESPDevice(
                                ESPConstants.TransportType.TRANSPORT_BLE,
                                ESPConstants.SecurityType.SECURITY_0
                            )
                        }
                        goToBLEProvisionLandingActivity(finalSecurityType)
                    }

                    1 -> {
                        if (isSec1) {
                            provisionManager!!.createESPDevice(
                                ESPConstants.TransportType.TRANSPORT_SOFTAP,
                                ESPConstants.SecurityType.SECURITY_1
                            )
                        } else {
                            provisionManager!!.createESPDevice(
                                ESPConstants.TransportType.TRANSPORT_SOFTAP,
                                ESPConstants.SecurityType.SECURITY_0
                            )
                        }
                        goToWiFiProvisionLandingActivity(finalSecurityType)
                    }
                }
                dialog.dismiss()
            }
            builder.show()
        }
    }

    private fun askForLocation() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(true)
        builder.setMessage(R.string.dialog_msg_gps)

        // Set up the buttons
        builder.setPositiveButton(
            R.string.btn_ok
        ) { dialog, which ->
            startActivityForResult(
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
                REQUEST_LOCATION
            )
        }

        builder.setNegativeButton(
            R.string.btn_cancel
        ) { dialog, which -> dialog.cancel() }

        builder.show()
    }

    private val isLocationEnabled: Boolean
        get() {
            var gps_enabled = false
            var network_enabled = false
            val lm = applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager

            try {
                gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            } catch (ex: Exception) {
            }

            try {
                network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            } catch (ex: Exception) {
            }

            Log.d(TAG, "GPS Enabled : $gps_enabled , Network Enabled : $network_enabled")

            val result = gps_enabled || network_enabled
            return result
        }

    private fun gotoQrCodeActivity() {
        val intent = Intent(this@EspMainActivity, AddDeviceActivity::class.java)
        startActivity(intent)
    }

    private fun goToBLEProvisionLandingActivity(securityType: Int) {
        val intent = Intent(this@EspMainActivity, BLEProvisionLanding::class.java)
        intent.putExtra(AppConstants.KEY_SECURITY_TYPE, securityType)
        startActivity(intent)
    }

    private fun goToWiFiProvisionLandingActivity(securityType: Int) {
        val intent = Intent(this@EspMainActivity, ProvisionLanding::class.java)
        intent.putExtra(AppConstants.KEY_SECURITY_TYPE, securityType)
        startActivity(intent)
    }
}
