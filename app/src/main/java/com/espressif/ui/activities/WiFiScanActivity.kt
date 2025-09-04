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

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.espressif.AppConstants
import com.espressif.provisioning.DeviceConnectionEvent
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.provisioning.WiFiAccessPoint
import com.espressif.provisioning.listeners.WiFiScanListener
import com.espressif.ui.adapters.WiFiListAdapter
import com.espressif.wifi_provisioning.R
import com.espressif.wifi_provisioning.databinding.ActivityWifiScanListBinding
import com.google.android.material.textfield.TextInputLayout
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class WiFiScanActivity : AppCompatActivity() {

    companion object {
        private val TAG: String = WiFiScanActivity::class.java.simpleName
    }

    private lateinit var binding: ActivityWifiScanListBinding

    private lateinit var provisionManager: ESPProvisionManager
    private lateinit var handler: Handler
    private lateinit var wifiAPList: ArrayList<WiFiAccessPoint>
    private lateinit var adapter: WiFiListAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWifiScanListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()

        wifiAPList = ArrayList()
        handler = Handler()
        provisionManager = ESPProvisionManager.getInstance(applicationContext)

        val deviceName = provisionManager.espDevice.deviceName
        val wifiMsg = String.format(getString(R.string.setup_instructions), deviceName)
        val tvWifiMsg = findViewById<TextView>(R.id.wifi_message)
        tvWifiMsg.text = wifiMsg

        binding.layoutWifiList.btnRefresh.setOnClickListener(refreshClickListener)
        adapter = WiFiListAdapter(this, R.id.tv_wifi_name, wifiAPList)

        // Assign adapter to ListView
        binding.layoutWifiList.wifiApList.adapter = adapter
        binding.layoutWifiList.wifiApList.onItemClickListener =
            OnItemClickListener { adapterView, view, pos, l ->
                Log.d(TAG, "Device to be connected -" + wifiAPList[pos])
                val ssid = wifiAPList[pos].wifiName
                if (ssid == getString(R.string.join_other_network)) {
                    askForNetwork(wifiAPList[pos].wifiName, wifiAPList[pos].security)
                } else if (wifiAPList[pos].security == ESPConstants.WIFI_OPEN.toInt()) {
                    goForProvisioning(wifiAPList[pos].wifiName, "")
                } else {
                    askForNetwork(wifiAPList[pos].wifiName, wifiAPList[pos].security)
                }
            }

        binding.layoutWifiList.wifiApList.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom -> }

        EventBus.getDefault().register(this)
        startWifiScan()
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun onBackPressed() {
        provisionManager.espDevice.disconnectDevice()
        super.onBackPressed()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: DeviceConnectionEvent) {
        Log.d(TAG, "On Device Connection Event RECEIVED : " + event.eventType)

        when (event.eventType) {
            ESPConstants.EVENT_DEVICE_DISCONNECTED -> if (!isFinishing) {
                showAlertForDeviceDisconnected()
            }
        }
    }

    private fun initViews() {
        setToolbar()
        binding.layoutWifiList.wifiProgressIndicator.visibility = View.VISIBLE
    }

    private fun setToolbar() {
        setSupportActionBar(binding.titleBar.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        supportActionBar!!.setTitle(R.string.title_activity_wifi_scan_list)
        binding.titleBar.toolbar.navigationIcon =
            AppCompatResources.getDrawable(this, R.drawable.ic_arrow_left)
        binding.titleBar.toolbar.setNavigationOnClickListener {
            provisionManager.espDevice?.disconnectDevice()
            finish()
        }
    }

    private fun startWifiScan() {
        Log.d(TAG, "Start Wi-Fi Scan")
        wifiAPList.clear()

        runOnUiThread { updateProgressAndScanBtn(true) }

        handler.postDelayed(stopScanningTask, 15000)

        provisionManager.espDevice.scanNetworks(object : WiFiScanListener {
            override fun onWifiListReceived(wifiList: ArrayList<WiFiAccessPoint>) {
                runOnUiThread {
                    wifiAPList.addAll(wifiList)
                    completeWifiList()
                }
            }

            override fun onWiFiScanFailed(e: Exception) {
                Log.e(TAG, "onWiFiScanFailed")
                e.printStackTrace()
                runOnUiThread {
                    updateProgressAndScanBtn(false)
                    Toast.makeText(
                        this@WiFiScanActivity,
                        "Failed to get Wi-Fi scan list",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        })
    }

    private fun completeWifiList() {
        // Add "Join network" Option as a list item

        val wifiAp = WiFiAccessPoint()
        wifiAp.wifiName = getString(R.string.join_other_network)
        wifiAPList.add(wifiAp)

        updateProgressAndScanBtn(false)
        handler.removeCallbacks(stopScanningTask)
    }

    private fun askForNetwork(ssid: String, authMode: Int) {
        val inflater = this.layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_wifi_network, null)
        val etSsid = dialogView.findViewById<EditText>(R.id.et_ssid)
        val etPassword = dialogView.findViewById<EditText>(R.id.et_password)

        var title = getString(R.string.join_other_network)
        if (ssid != title) {
            title = ssid
            etSsid.visibility = View.GONE
        }

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle(title)
            .setPositiveButton(R.string.btn_provision, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .create()

        alertDialog.setOnShowListener { dialog ->
            val buttonPositive =
                (dialog as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE)
            buttonPositive.setOnClickListener {
                var password = etPassword.text.toString()
                if (ssid == getString(R.string.join_other_network)) {
                    val networkName = etSsid.text.toString()

                    if (TextUtils.isEmpty(networkName)) {
                        etSsid.error = getString(R.string.error_ssid_empty)
                    } else {
                        dialog.dismiss()
                        goForProvisioning(networkName, password)
                    }
                } else {
                    if (TextUtils.isEmpty(password)) {
                        if (authMode != ESPConstants.WIFI_OPEN.toInt()) {
                            val passwordLayout =
                                dialogView.findViewById<TextInputLayout>(R.id.layout_password)
                            passwordLayout.error = getString(R.string.error_password_empty)
                        } else {
                            dialog.dismiss()
                            goForProvisioning(ssid, password)
                        }
                    } else {
                        if (authMode == ESPConstants.WIFI_OPEN.toInt()) {
                            password = ""
                        }
                        dialog.dismiss()
                        goForProvisioning(ssid, password)
                    }
                }
            }
            val buttonNegative =
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
            buttonNegative.setOnClickListener { dialog.dismiss() }
        }

        alertDialog.show()
    }

    private fun goForProvisioning(ssid: String, password: String) {
        finish()
        val provisionIntent = Intent(applicationContext, ProvisionActivity::class.java)
        provisionIntent.putExtras(intent)
        provisionIntent.putExtra(AppConstants.KEY_WIFI_SSID, ssid)
        provisionIntent.putExtra(AppConstants.KEY_WIFI_PASSWORD, password)
        startActivity(provisionIntent)
    }

    private val refreshClickListener = View.OnClickListener { startWifiScan() }

    private val stopScanningTask = Runnable {
        updateProgressAndScanBtn(
            false
        )
    }

    /**
     * This method will update UI (Scan button enable / disable and progressbar visibility)
     */
    private fun updateProgressAndScanBtn(isScanning: Boolean) {
        if (isScanning) {
            binding.layoutWifiList.wifiProgressIndicator.visibility = View.VISIBLE
            binding.layoutWifiList.wifiApList.visibility = View.GONE
            binding.layoutWifiList.btnRefresh.visibility = View.GONE
        } else {
            binding.layoutWifiList.wifiProgressIndicator.visibility = View.GONE
            binding.layoutWifiList.wifiApList.visibility = View.VISIBLE
            binding.layoutWifiList.btnRefresh.visibility = View.VISIBLE
            adapter.notifyDataSetChanged()
        }
    }

    private fun showAlertForDeviceDisconnected() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setTitle(R.string.error_title)
        builder.setMessage(R.string.dialog_msg_ble_device_disconnection)

        // Set up the buttons
        builder.setPositiveButton(
            R.string.btn_ok
        ) { dialog, which ->
            dialog.dismiss()
            finish()
        }
        builder.show()
    }
}
