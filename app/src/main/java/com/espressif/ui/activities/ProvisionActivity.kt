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
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.espressif.AppConstants
import com.espressif.provisioning.DeviceConnectionEvent
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPConstants.ProvisionFailureReason
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.provisioning.listeners.ProvisionListener
import com.espressif.provisioning.listeners.ResponseListener
import com.espressif.ui.utils.applySystemBarsAsPadding
import com.espressif.wifi_provisioning.R
import com.espressif.wifi_provisioning.databinding.ActivityProvisionBinding
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class ProvisionActivity : AppCompatActivity() {

    companion object {
        private val TAG: String = ProvisionActivity::class.java.simpleName
    }

    private lateinit var binding: ActivityProvisionBinding
    private lateinit var provisionManager: ESPProvisionManager

    private var ssidValue: String? = null
    private var passphraseValue: String? = ""
    private var dataset: String? = null
    private var isProvisioningCompleted = false
    private var errorMessage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProvisionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarsAsPadding()

        val intent = intent
        ssidValue = intent.getStringExtra(AppConstants.KEY_WIFI_SSID)
        passphraseValue = intent.getStringExtra(AppConstants.KEY_WIFI_PASSWORD)
        dataset = intent.getStringExtra(AppConstants.KEY_THREAD_DATASET)
        provisionManager = ESPProvisionManager.getInstance(applicationContext)
        initViews()
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        EventBus.getDefault().register(this)

        Log.d(TAG, "Selected AP -$ssidValue")
        showLoading()
        doProvisioning()
    }

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            provisionManager.espDevice.disconnectDevice()
            finish()
        }
    }

    override fun onDestroy() {
        EventBus.getDefault().unregister(this)
        super.onDestroy()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: DeviceConnectionEvent) {
        Log.d(TAG, "On Device Connection Event RECEIVED : " + event.eventType)

        when (event.eventType) {
            ESPConstants.EVENT_DEVICE_DISCONNECTED -> if (!isFinishing && !isProvisioningCompleted) {
                showAlertForDeviceDisconnected()
            }
        }
    }

    private val okBtnClickListener = View.OnClickListener {
        provisionManager.espDevice?.disconnectDevice()
        finish()
    }

    private fun initViews() {
        setToolbar()
        binding.btnOk.ivArrow.visibility = View.GONE
        binding.btnOk.textBtn.setText(R.string.btn_ok)
        binding.btnOk.layoutBtn.setOnClickListener(okBtnClickListener)
    }

    private fun setToolbar() {
        setSupportActionBar(binding.titleBar.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(false)
        supportActionBar!!.setDisplayShowHomeEnabled(false)
        supportActionBar!!.setTitle(R.string.title_activity_provisioning)
    }

    private fun doProvisioning() {
        binding.ivTick1.visibility = View.GONE
        binding.provProgress1.visibility = View.VISIBLE

        if (!TextUtils.isEmpty(dataset)) {
            provisionManager.espDevice.provision(dataset, object : ProvisionListener {
                override fun createSessionFailed(e: Exception) {
                    runOnUiThread {
                        binding.ivTick1.setImageResource(R.drawable.ic_error)
                        binding.ivTick1.visibility = View.VISIBLE
                        binding.provProgress1.visibility = View.GONE
                        binding.tvProvError1.visibility = View.VISIBLE
                        binding.tvProvError1.setText(R.string.error_session_creation)
                        binding.tvProvError.visibility = View.VISIBLE
                        hideLoading()
                    }
                }

                override fun wifiConfigSent() {
                    runOnUiThread {
                        binding.ivTick1.setImageResource(R.drawable.ic_checkbox_on)
                        binding.ivTick1.visibility = View.VISIBLE
                        binding.provProgress1.visibility = View.GONE
                        binding.ivTick2.visibility = View.GONE
                        binding.provProgress2.visibility = View.VISIBLE
                    }
                }

                override fun wifiConfigFailed(e: Exception) {
                    runOnUiThread {
                        binding.ivTick1.setImageResource(R.drawable.ic_error)
                        binding.ivTick1.visibility = View.VISIBLE
                        binding.provProgress1.visibility = View.GONE
                        binding.tvProvError1.visibility = View.VISIBLE
                        binding.tvProvError1.setText(R.string.error_prov_thread_step_1)
                        binding.tvProvError.visibility = View.VISIBLE
                        hideLoading()
                    }
                }

                override fun wifiConfigApplied() {
                    runOnUiThread {
                        binding.ivTick2.setImageResource(R.drawable.ic_checkbox_on)
                        binding.ivTick2.visibility = View.VISIBLE
                        binding.provProgress2.visibility = View.GONE
                        binding.ivTick3.visibility = View.GONE
                        binding.provProgress3.visibility = View.VISIBLE
                    }
                }

                override fun wifiConfigApplyFailed(e: Exception) {
                    runOnUiThread {
                        binding.ivTick2.setImageResource(R.drawable.ic_error)
                        binding.ivTick2.visibility = View.VISIBLE
                        binding.provProgress2.visibility = View.GONE
                        binding.tvProvError2.visibility = View.VISIBLE
                        binding.tvProvError2.setText(R.string.error_prov_thread_step_2)
                        binding.tvProvError.visibility = View.VISIBLE
                        hideLoading()
                    }
                }

                override fun provisioningFailedFromDevice(failureReason: ProvisionFailureReason) {
                    runOnUiThread {
                        when (failureReason) {
                            ProvisionFailureReason.AUTH_FAILED -> binding.tvProvError3.setText(R.string.error_dataset_invalid)
                            ProvisionFailureReason.NETWORK_NOT_FOUND -> binding.tvProvError3.setText(
                                R.string.error_network_not_found
                            )

                            ProvisionFailureReason.DEVICE_DISCONNECTED, ProvisionFailureReason.UNKNOWN -> binding.tvProvError3.setText(
                                R.string.error_prov_step_3
                            )
                        }
                        binding.ivTick3.setImageResource(R.drawable.ic_error)
                        binding.ivTick3.visibility = View.VISIBLE
                        binding.provProgress3.visibility = View.GONE
                        binding.tvProvError3.visibility = View.VISIBLE
                        binding.tvProvError.visibility = View.VISIBLE
                        hideLoading()
                    }
                }

                override fun deviceProvisioningSuccess() {
                    runOnUiThread {
                        isProvisioningCompleted = true
                        binding.ivTick3.setImageResource(R.drawable.ic_checkbox_on)
                        binding.ivTick3.visibility = View.VISIBLE
                        binding.provProgress3.visibility = View.GONE
                        hideLoading()
                    }
                }

                override fun onProvisioningFailed(e: Exception) {
                    runOnUiThread {
                        binding.ivTick3.setImageResource(R.drawable.ic_error)
                        binding.ivTick3.visibility = View.VISIBLE
                        binding.provProgress3.visibility = View.GONE
                        binding.tvProvError3.visibility = View.VISIBLE
                        binding.tvProvError3.setText(R.string.error_prov_step_3)
                        binding.tvProvError.visibility = View.VISIBLE
                        hideLoading()
                    }
                }
            })
        } else {
            provisionManager.espDevice.provision(
                ssidValue,
                passphraseValue,
                object : ProvisionListener {
                    override fun createSessionFailed(e: Exception) {
                        runOnUiThread {
                            binding.ivTick1.setImageResource(R.drawable.ic_error)
                            binding.ivTick1.visibility = View.VISIBLE
                            binding.provProgress1.visibility = View.GONE
                            binding.tvProvError1.visibility = View.VISIBLE
                            binding.tvProvError1.setText(R.string.error_session_creation)
                            binding.tvProvError.visibility = View.VISIBLE
                            hideLoading()
                        }
                    }

                    override fun wifiConfigSent() {
                        runOnUiThread {
                            binding.ivTick1.setImageResource(R.drawable.ic_checkbox_on)
                            binding.ivTick1.visibility = View.VISIBLE
                            binding.provProgress1.visibility = View.GONE
                            binding.ivTick2.visibility = View.GONE
                            binding.provProgress2.visibility = View.VISIBLE
                        }
                    }

                    override fun wifiConfigFailed(e: Exception) {
                        runOnUiThread {
                            binding.ivTick1.setImageResource(R.drawable.ic_error)
                            binding.ivTick1.visibility = View.VISIBLE
                            binding.provProgress1.visibility = View.GONE
                            binding.tvProvError1.visibility = View.VISIBLE
                            binding.tvProvError1.setText(R.string.error_prov_step_1)
                            binding.tvProvError.visibility = View.VISIBLE
                            hideLoading()
                            errorMessage = getString(R.string.error_prov_step_1)
                        }
                    }

                    override fun wifiConfigApplied() {
                        runOnUiThread {
                            binding.ivTick2.setImageResource(R.drawable.ic_checkbox_on)
                            binding.ivTick2.visibility = View.VISIBLE
                            binding.provProgress2.visibility = View.GONE
                            binding.ivTick3.visibility = View.GONE
                            binding.provProgress3.visibility = View.VISIBLE
                        }
                    }

                    override fun wifiConfigApplyFailed(e: Exception) {
                        runOnUiThread {
                            binding.ivTick2.setImageResource(R.drawable.ic_error)
                            binding.ivTick2.visibility = View.VISIBLE
                            binding.provProgress2.visibility = View.GONE
                            binding.tvProvError2.visibility = View.VISIBLE
                            binding.tvProvError2.setText(R.string.error_prov_step_2)
                            binding.tvProvError.visibility = View.VISIBLE
                            hideLoading()
                            errorMessage = getString(R.string.error_prov_step_2)
                        }
                    }

                    override fun provisioningFailedFromDevice(failureReason: ProvisionFailureReason) {
                        var isDeviceConnected = true
                        runOnUiThread {
                            when (failureReason) {
                                ProvisionFailureReason.AUTH_FAILED -> {
                                    binding.tvProvError3.setText(R.string.error_authentication_failed)
                                    errorMessage = getString(R.string.error_authentication_failed)
                                }

                                ProvisionFailureReason.NETWORK_NOT_FOUND -> {
                                    binding.tvProvError3.setText(R.string.error_network_not_found)
                                    errorMessage = getString(R.string.error_network_not_found)
                                }

                                ProvisionFailureReason.DEVICE_DISCONNECTED, ProvisionFailureReason.UNKNOWN -> {
                                    binding.tvProvError3.setText(R.string.error_prov_step_3)
                                    errorMessage = getString(R.string.error_prov_step_3)
                                    isDeviceConnected = true
                                }
                            }

                            binding.ivTick3.setImageResource(R.drawable.ic_error)
                            binding.ivTick3.visibility = View.VISIBLE
                            binding.provProgress3.visibility = View.GONE
                            binding.tvProvError3.visibility = View.VISIBLE
                            binding.tvProvError.visibility = View.VISIBLE
                            hideLoading()
                            if (isDeviceConnected) {
                                sendWifiResetCommand()
                            }
                        }
                    }

                    override fun deviceProvisioningSuccess() {
                        runOnUiThread {
                            isProvisioningCompleted = true
                            binding.ivTick3.setImageResource(R.drawable.ic_checkbox_on)
                            binding.ivTick3.visibility = View.VISIBLE
                            binding.provProgress3.visibility = View.GONE
                            hideLoading()
                        }
                    }

                    override fun onProvisioningFailed(e: Exception) {
                        runOnUiThread {
                            binding.ivTick3.setImageResource(R.drawable.ic_error)
                            binding.ivTick3.visibility = View.VISIBLE
                            binding.provProgress3.visibility = View.GONE
                            binding.tvProvError3.visibility = View.VISIBLE
                            binding.tvProvError3.setText(R.string.error_prov_step_3)
                            binding.tvProvError.visibility = View.VISIBLE
                            hideLoading()
                            errorMessage = getString(R.string.error_prov_step_3)
                        }
                    }
                })
        }
    }

    private fun showLoading() {
        binding.btnOk.layoutBtn.isEnabled = false
        binding.btnOk.layoutBtn.alpha = 0.5f
    }

    fun hideLoading() {
        binding.btnOk.layoutBtn.isEnabled = true
        binding.btnOk.layoutBtn.alpha = 1f
    }

    private fun showAlertForDeviceDisconnected() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setTitle(R.string.error_title)
        builder.setMessage(R.string.dialog_msg_ble_device_disconnection)

        // Set up the buttons
        builder.setPositiveButton(
            R.string.btn_ok
        ) { dialog, _ ->
            dialog.dismiss()
            finish()
        }
        builder.show()
    }

    /**
     * Send WiFi reset command to device when authentication failure error received in provisioning.
     * The resetWifiStatus method will check if session is established internally
     */
    private fun sendWifiResetCommand() {

        provisionManager.espDevice.resetWifiStatus(object : ResponseListener {
            override fun onSuccess(returnData: ByteArray?) {
                runOnUiThread {
                    Log.d(TAG, "Success received for sending WiFi reset command")
                    showReenterPasswordAlert()
                }
            }

            override fun onFailure(e: Exception) {
                runOnUiThread {
                    // Log error but don't block UI - reset is best effort
                    Log.e(TAG, "Failed to send WiFi reset command", e)
                    showResetPasswordFailedAlert("Failed to send WiFi reset command: ${e.message}")
                }
            }
        })
    }

    /**
     * Show alert dialog to re-enter WiFi password
     */
    private fun showReenterPasswordAlert() {
        val title = getString(R.string.title_activity_provisioning)
        val wifiResetMsg = getString(R.string.wifi_reset_message)
        var alertMsg = wifiResetMsg
        if (!errorMessage.isNullOrEmpty()) {
            alertMsg = "$errorMessage $wifiResetMsg"
        }

        val alert = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(alertMsg)
            .setCancelable(false)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                showPasswordInputDialog()
            }
            .setNegativeButton(R.string.btn_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
        alert.show()
    }

    /**
     * Show password input dialog for re-provisioning
     */
    private fun showPasswordInputDialog() {
        val inflater = this.layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_wifi_network, null)
        val etSsid = dialogView.findViewById<EditText>(R.id.et_ssid)
        val etPassword = dialogView.findViewById<EditText>(R.id.et_password)
        val passwordLayout = dialogView.findViewById<TextInputLayout>(R.id.layout_password)

        // Hide SSID field since we already know it
        etSsid.visibility = View.GONE

        // Set SSID as title
        val title = ssidValue ?: getString(R.string.join_other_network)

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle(title)
            .setPositiveButton(R.string.btn_provision, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .setCancelable(false)
            .create()

        alertDialog.setOnShowListener { dialog ->
            val buttonPositive = (dialog as AlertDialog).getButton(DialogInterface.BUTTON_POSITIVE)
            buttonPositive.setOnClickListener {
                val password = etPassword.text.toString()

                // Validate password if network is not open
                // Note: We assume it's not open since authentication failed
                if (TextUtils.isEmpty(password)) {
                    passwordLayout.error = getString(R.string.error_password_empty)
                } else {
                    dialog.dismiss()
                    // Update password and re-provision
                    passphraseValue = password
                    resetUIForRetry()
                    doProvisioning()
                }
            }

            val buttonNegative = dialog.getButton(DialogInterface.BUTTON_NEGATIVE)
            buttonNegative.setOnClickListener {
                dialog.dismiss()
            }
        }

        alertDialog.show()
    }

    /**
     * Reset UI state before retrying provisioning
     */
    private fun resetUIForRetry() {
        // Hide error messages
        binding.tvProvError1.visibility = View.GONE
        binding.tvProvError2.visibility = View.GONE
        binding.tvProvError3.visibility = View.GONE
        binding.tvProvError.visibility = View.GONE

        // Hide images
        binding.ivTick1.visibility = View.GONE
        binding.ivTick2.visibility = View.GONE
        binding.ivTick3.visibility = View.GONE

        // Hide progress indicators
        binding.provProgress1.visibility = View.GONE
        binding.provProgress2.visibility = View.GONE
        binding.provProgress3.visibility = View.GONE

        // Reset error message
        errorMessage = null
    }

    /**
     * Show alert dialog when reset command fails
     */
    private fun showResetPasswordFailedAlert(message: String) {
        val alert = AlertDialog.Builder(this)
            .setTitle(getString(R.string.title_activity_provisioning))
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton(R.string.btn_ok) { _, _ ->
                finish()
            }
            .create()
        alert.show()
    }
}
