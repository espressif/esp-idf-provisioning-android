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

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.espressif.AppConstants
import com.espressif.provisioning.DeviceConnectionEvent
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.provisioning.listeners.ResponseListener
import com.espressif.wifi_provisioning.R
import com.espressif.wifi_provisioning.databinding.ActivityPopBinding
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ProofOfPossessionActivity : AppCompatActivity() {

    companion object {
        private val TAG: String = ProofOfPossessionActivity::class.java.simpleName
    }

    private lateinit var binding: ActivityPopBinding

    private var deviceName: String? = null
    private lateinit var provisionManager: ESPProvisionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPopBinding.inflate(layoutInflater)
        setContentView(binding.root)

        provisionManager = ESPProvisionManager.getInstance(applicationContext)
        deviceName = provisionManager.espDevice!!.deviceName
        initViews()
        EventBus.getDefault().register(this)

        if (!TextUtils.isEmpty(deviceName)) {
            val popText = getString(R.string.pop_instruction) + " " + deviceName
            binding.tvPop.text = popText
        }

        val pop = resources.getString(R.string.proof_of_possesion)

        if (!TextUtils.isEmpty(pop)) {
            binding.etPop.setText(pop)
            binding.etPop.setSelection(binding.etPop.text.length)
        }
        binding.etPop.requestFocus()
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    override fun onBackPressed() {
        provisionManager.espDevice?.disconnectDevice()
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

    private val nextBtnClickListener = View.OnClickListener {
        val pop = binding.etPop.text.toString()
        Log.d(TAG, "POP : $pop")

        binding.btnNext.layoutBtn.isEnabled = false
        binding.btnNext.layoutBtn.alpha = 0.5f
        binding.btnNext.progressIndicator.visibility = View.VISIBLE
        binding.tvErrorPop.visibility = View.INVISIBLE
        provisionManager.espDevice.proofOfPossession = pop
        provisionManager.espDevice.initSession(object : ResponseListener {

            override fun onSuccess(returnData: ByteArray?) {
                runOnUiThread {
                    binding.btnNext.layoutBtn.isEnabled = true
                    binding.btnNext.layoutBtn.alpha = 1f
                    binding.btnNext.progressIndicator.visibility = View.GONE
                    val deviceCaps = provisionManager.espDevice.deviceCapabilities
                    if (deviceCaps.contains(AppConstants.CAPABILITY_WIFI_SCAN)) {
                        goToWiFiScanListActivity()
                    } else if (deviceCaps.contains(AppConstants.CAPABILITY_THREAD_SCAN)) {
                        goToThreadScanActivity(true)
                    } else if (deviceCaps.contains(AppConstants.CAPABILITY_THREAD_PROV)) {
                        goToThreadScanActivity(false)
                    } else {
                        goToWiFiConfigActivity()
                    }
                }
            }

            override fun onFailure(e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    binding.btnNext.layoutBtn.isEnabled = true
                    binding.btnNext.layoutBtn.alpha = 1f
                    binding.btnNext.progressIndicator.visibility = View.GONE
                    binding.tvErrorPop.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun initViews() {
        setToolbar()
        binding.btnNext.textBtn.setText(R.string.btn_next)
        binding.btnNext.layoutBtn.setOnClickListener(nextBtnClickListener)
    }

    private fun setToolbar() {
        setSupportActionBar(binding.titleBar.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        supportActionBar!!.setTitle(R.string.title_activity_pop)
        binding.titleBar.toolbar.navigationIcon =
            AppCompatResources.getDrawable(this, R.drawable.ic_arrow_left)
        binding.titleBar.toolbar.setNavigationOnClickListener {
            provisionManager.espDevice?.disconnectDevice()
            finish()
        }
    }

    private fun goToWiFiScanListActivity() {
        val wifiListIntent = Intent(applicationContext, WiFiScanActivity::class.java)
        wifiListIntent.putExtras(intent)
        startActivity(wifiListIntent)
        finish()
    }

    private fun goToThreadScanActivity(scanCapAvailable: Boolean) {
        val threadConfigIntent = Intent(applicationContext, ThreadConfigActivity::class.java)
        threadConfigIntent.putExtras(intent)
        threadConfigIntent.putExtra(AppConstants.KEY_THREAD_SCAN_AVAILABLE, scanCapAvailable)
        startActivity(threadConfigIntent)
        finish()
    }

    private fun goToWiFiConfigActivity() {
        val wifiConfigIntent = Intent(applicationContext, WiFiConfigActivity::class.java)
        wifiConfigIntent.putExtras(intent)
        startActivity(wifiConfigIntent)
        finish()
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
