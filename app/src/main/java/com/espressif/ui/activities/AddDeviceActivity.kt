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
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.SupplicantState
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.budiyev.android.codescanner.CodeScanner
import com.espressif.AppConstants
import com.espressif.provisioning.DeviceConnectionEvent
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.ESPDevice
import com.espressif.provisioning.ESPProvisionManager
import com.espressif.provisioning.listeners.QRCodeScanListener
import com.espressif.ui.utils.Utils
import com.espressif.wifi_provisioning.R
import com.espressif.wifi_provisioning.databinding.ActivityAddDeviceBinding
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONException
import org.json.JSONObject

class AddDeviceActivity : AppCompatActivity() {

    companion object {
        private val TAG = AddDeviceActivity::class.java.simpleName
        private const val REQUEST_CAMERA_PERMISSION = 1
        private const val REQUEST_ACCESS_FINE_LOCATION = 2
        private const val REQUEST_ENABLE_BT = 3
    }

    private lateinit var binding: ActivityAddDeviceBinding
    private lateinit var sharedPreferences: SharedPreferences

    private var espDevice: ESPDevice? = null
    private lateinit var provisionManager: ESPProvisionManager

    private var codeScanner: CodeScanner? = null

    private var isQrCodeDataReceived = false
    private lateinit var intent: Intent
    private var buttonClicked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)
        intent = Intent()
        sharedPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE)
        provisionManager = ESPProvisionManager.getInstance(applicationContext).scanTimeout(10_000)
        initViews()
        EventBus.getDefault().register(this)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionErrorText()

        if (areAllPermissionsGranted()) {
            binding.layoutQrCodeTxt.visibility = View.VISIBLE
            binding.layoutPermissionError.visibility = View.GONE
            binding.btnGetPermission.layoutBtnRemove.visibility = View.GONE
            openCamera()
        } else {
            binding.layoutQrCodeTxt.visibility = View.GONE
            binding.layoutPermissionError.visibility = View.VISIBLE
            binding.btnGetPermission.layoutBtnRemove.visibility = View.VISIBLE
        }

        // This condition is to get event of cancel button of "try again" popup. Because Android 10 is not giving event on cancel button click if network is not found.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && espDevice != null && espDevice!!.transportType == ESPConstants.TransportType.TRANSPORT_SOFTAP) {
            val ssid = wifiSsid
            Log.d(TAG, "Currently connected WiFi SSID : $ssid")
            Log.d(TAG, "Device Name  : ${espDevice!!.deviceName}")
            if (!TextUtils.isEmpty(ssid) && ssid != espDevice!!.deviceName) {
                Log.e(TAG, "Device is not connected")
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        codeScanner?.stopPreview()
    }

    override fun onDestroy() {
        hideLoading()
        EventBus.getDefault().unregister(this)
        codeScanner?.releaseResources()
        super.onDestroy()
    }

    override fun onBackPressed() {
        provisionManager.espDevice?.disconnectDevice()
        super.onBackPressed()
    }

    private fun areAllPermissionsGranted(): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        val locationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetoothScanPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            val bluetoothConnectPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            cameraPermission && locationPermission && bluetoothScanPermission && bluetoothConnectPermission
        } else {
            cameraPermission && locationPermission
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updatePermissionErrorText()
        Log.e(TAG, "onRequestPermissionsResult , requestCode : $requestCode")

        if (areAllPermissionsGranted()) {
            binding.layoutQrCodeTxt.visibility = View.VISIBLE
            binding.layoutPermissionError.visibility = View.GONE
            binding.btnGetPermission.layoutBtnRemove.visibility = View.GONE
            openCamera()
        } else {
            binding.scannerView.visibility = View.GONE
            binding.layoutQrCodeTxt.visibility = View.GONE
            binding.layoutPermissionError.visibility = View.VISIBLE
            binding.btnGetPermission.layoutBtnRemove.visibility = View.VISIBLE

            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED -> {
                    binding.tvPermissionError.setText(R.string.error_camera_permission)
                    binding.ivPermissionError.setImageResource(R.drawable.ic_no_camera_permission)
                }

                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED -> {
                    binding.tvPermissionError.setText(R.string.error_location_permission)
                    binding.ivPermissionError.setImageResource(R.drawable.ic_no_location_permission)
                    showLocationPermissionAlertDialog()
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) != PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(
                                    this,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) != PackageManager.PERMISSION_GRANTED) -> {
                    binding.tvPermissionError.setText(R.string.error_nearby_devices_permission)
                    binding.ivPermissionError.setImageResource(R.drawable.ic_no_bluetooth_permission)
                    showLocationPermissionAlertDialog()
                }

                else -> {
                    binding.tvPermissionError.text = ""
                    binding.ivPermissionError.setImageDrawable(null)
                }
            }
        }
    }

    private fun updatePermissionErrorText() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED -> {
                binding.tvPermissionError.setText(R.string.error_camera_permission)
                binding.ivPermissionError.setImageResource(R.drawable.ic_no_camera_permission)
            }

            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED -> {
                binding.tvPermissionError.setText(R.string.error_location_permission)
                binding.ivPermissionError.setImageResource(R.drawable.ic_no_location_permission)
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) != PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED) -> {
                binding.tvPermissionError.setText(R.string.error_nearby_devices_permission)
                binding.ivPermissionError.setImageResource(R.drawable.ic_no_bluetooth_permission)
            }

            else -> {
                binding.tvPermissionError.text = ""
                binding.ivPermissionError.setImageDrawable(null)
            }
        }
    }

    private fun navigateToAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                if (resultCode == RESULT_OK) {
                    startProvisioningFlow()
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: DeviceConnectionEvent) {
        Log.d(TAG, "On Device Connection Event RECEIVED : ${event.eventType}")

        when (event.eventType) {
            ESPConstants.EVENT_DEVICE_CONNECTED -> {
                Log.d(TAG, "Device Connected Event Received")
                setSecurityTypeFromVersionInfo()
            }

            ESPConstants.EVENT_DEVICE_DISCONNECTED -> {
                if (espDevice != null && espDevice!!.transportType == ESPConstants.TransportType.TRANSPORT_BLE) {
                    Toast.makeText(this@AddDeviceActivity, "Device disconnected", Toast.LENGTH_LONG)
                        .show()
                    finish()
                } else {
                    if (!isFinishing) {
                        askForManualDeviceConnection()
                    }
                }
            }

            ESPConstants.EVENT_DEVICE_CONNECTION_FAILED -> {
                if (espDevice != null && espDevice!!.transportType == ESPConstants.TransportType.TRANSPORT_BLE) {
                    alertForDeviceNotSupported("Failed to connect with device")
                } else {
                    if (!isFinishing) {
                        askForManualDeviceConnection()
                    }
                }
            }
        }
    }

    private val btnAddManuallyClickListener = View.OnClickListener {
        val deviceType = sharedPreferences.getString(
            AppConstants.KEY_DEVICE_TYPES,
            AppConstants.DEVICE_TYPE_DEFAULT
        )

        if (deviceType == AppConstants.DEVICE_TYPE_BLE || deviceType == AppConstants.DEVICE_TYPE_BOTH) {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bleAdapter = bluetoothManager.adapter

            if (!bleAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            this@AddDeviceActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                    } else {
                        Log.e(TAG, "BLUETOOTH_CONNECT permission is not granted.")
                        return@OnClickListener
                    }
                } else {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                }
            } else {
                startProvisioningFlow()
            }
        } else {
            startProvisioningFlow()
        }
    }

    private val cancelBtnClickListener = View.OnClickListener {
        provisionManager.espDevice?.disconnectDevice()
        setResult(RESULT_CANCELED, intent)
        finish()
    }

    private val btnGetPermissionClickListener = View.OnClickListener {
        buttonClicked = true

        when {
            ContextCompat.checkSelfPermission(
                this@AddDeviceActivity,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED -> {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this@AddDeviceActivity,
                        Manifest.permission.CAMERA
                    )
                ) {
                    showCameraPermissionExplanation()
                } else {
                    requestCameraPermission()
                }
            }

            ContextCompat.checkSelfPermission(
                this@AddDeviceActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED -> {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this@AddDeviceActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                ) {
                    showLocationAndBluetoothPermissionExplanation(false)
                } else {
                    requestLocationAndBluetoothPermission()
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (ContextCompat.checkSelfPermission(
                        this@AddDeviceActivity,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this@AddDeviceActivity,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {

                    if (ActivityCompat.shouldShowRequestPermissionRationale(
                            this@AddDeviceActivity,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) ||
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            this@AddDeviceActivity,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                    ) {
                        showLocationAndBluetoothPermissionExplanation(true)
                    } else {
                        requestLocationAndBluetoothPermission()
                    }
                }
            }
        }
    }

    private fun showCameraPermissionExplanation() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setTitle(R.string.dialog_title_camera_permission)
        builder.setMessage(R.string.dialog_msg_camera_permission_use)
        builder.setPositiveButton(R.string.btn_ok) { dialog, _ ->
            requestCameraPermission()
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.btn_cancel) { dialog, _ ->
            dialog.dismiss()
            finish()
        }
        builder.show()
    }

    private fun showLocationAndBluetoothPermissionExplanation(includeBtPermission: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)

        if (includeBtPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                builder.setTitle(R.string.dialog_title_location_permission)
                builder.setMessage(R.string.dialog_msg_location_permission_use)
            } else {
                builder.setTitle(R.string.dialog_title_nearby_devices_permission)
                builder.setMessage(R.string.dialog_msg_nearby_devices_permission_use)
            }
        } else {
            builder.setTitle(R.string.dialog_title_location_permission)
            builder.setMessage(R.string.dialog_msg_location_permission_use)
        }

        builder.setPositiveButton(R.string.btn_ok) { dialog, _ ->
            requestLocationAndBluetoothPermission()
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.btn_cancel) { dialog, _ ->
            dialog.dismiss()
            finish()
        }
        builder.show()
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            REQUEST_CAMERA_PERMISSION
        )
    }

    private fun requestLocationAndBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ), REQUEST_ACCESS_FINE_LOCATION
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_ACCESS_FINE_LOCATION
            )
        }
    }

    private fun showLocationPermissionAlertDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)

        val messageResId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED -> R.string.error_location_permission

                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED -> R.string.error_nearby_devices_permission

                else -> R.string.error_location_permission
            }
        } else {
            R.string.error_location_permission
        }

        builder.setMessage(messageResId)
        builder.setPositiveButton(R.string.action_settings) { _, _ ->
            navigateToAppSettings()
        }
        builder.setNegativeButton(R.string.btn_cancel) { dialog, _ ->
            dialog.dismiss()
        }

        val alertDialog = builder.create()
        if (!isFinishing) {
            alertDialog.show()
        }
    }

    private fun initViews() {
        binding.titleBar.mainToolbarTitle.setText(R.string.title_activity_add_device)
        binding.titleBar.btnBack.visibility = View.GONE
        binding.titleBar.btnCancel.visibility = View.VISIBLE
        binding.titleBar.btnCancel.setOnClickListener(cancelBtnClickListener)

        codeScanner = CodeScanner(this, binding.scannerView)

        binding.btnAddDeviceManually.textBtn.setText(R.string.btn_no_qr_code)
        binding.btnAddDeviceManually.layoutBtnRemove.setOnClickListener(btnAddManuallyClickListener)

        binding.btnGetPermission.textBtn.setText(R.string.btn_get_permission)
        binding.btnGetPermission.layoutBtnRemove.setOnClickListener(btnGetPermissionClickListener)

        if (ActivityCompat.checkSelfPermission(
                this@AddDeviceActivity,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            openCamera()
        } else {
            ActivityCompat.requestPermissions(
                this@AddDeviceActivity,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    private fun openCamera() {

        val isPlayServicesAvailable = Utils.isPlayServicesAvailable(applicationContext)

        if (isPlayServicesAvailable) {
            binding.scannerView.visibility = View.GONE
            binding.cameraPreview.visibility = View.VISIBLE
            binding.overlay.visibility = View.VISIBLE
            binding.clearWindow.visibility = View.VISIBLE
            binding.qrFrame.layoutQrCodeFrame.visibility = View.VISIBLE
        } else {
            binding.scannerView.visibility = View.VISIBLE
            binding.cameraPreview.visibility = View.GONE
            binding.overlay.visibility = View.GONE
            binding.clearWindow.visibility = View.GONE
            binding.qrFrame.layoutQrCodeFrame.visibility = View.GONE
            codeScanner?.startPreview()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (isPlayServicesAvailable) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

                    cameraProviderFuture.addListener({
                        try {
                            // Start QR code scanning using ESPProvisionManager
                            provisionManager.scanQRCode(
                                binding.cameraPreview,
                                this,
                                qrCodeScanListener
                            )
                        } catch (exc: Exception) {
                            Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }, ContextCompat.getMainExecutor(this))
                } else {
                    provisionManager.scanQRCode(codeScanner, qrCodeScanListener)
                }
            } else {
                checkLocationAndBluetoothPermission()
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                if (isPlayServicesAvailable) {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

                    cameraProviderFuture.addListener({
                        try {
                            // Start QR code scanning using ESPProvisionManager
                            provisionManager.scanQRCode(
                                binding.cameraPreview,
                                this,
                                qrCodeScanListener
                            )
                        } catch (exc: Exception) {
                            Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }, ContextCompat.getMainExecutor(this))
                } else {
                    provisionManager.scanQRCode(codeScanner, qrCodeScanListener)
                }
            } else {
                checkLocationAndBluetoothPermission()
            }
        }
    }

    private fun checkLocationAndBluetoothPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED -> {
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
                ) {
                    showLocationAndBluetoothPermissionExplanation(false)
                } else {
                    requestLocationAndBluetoothPermission()
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {

                    if (ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) ||
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        )
                    ) {
                        showLocationAndBluetoothPermissionExplanation(true)
                    } else {
                        requestLocationAndBluetoothPermission()
                    }
                } else {
                    openCamera()
                }
            }

            else -> openCamera()
        }
    }

    private fun showLoading() {
        binding.loader.visibility = View.VISIBLE
        binding.loader.show()
    }

    private fun hideLoading() {
        binding.loader.hide()

        // Hide QR scanning UI
        binding.overlay.visibility = View.GONE
        binding.clearWindow.visibility = View.GONE
        binding.qrFrame.layoutQrCodeFrame.visibility = View.GONE
    }

    private val qrCodeScanListener = object : QRCodeScanListener {
        override fun qrCodeScanned() {
            runOnUiThread {
                showLoading()
                val vib = getSystemService(VIBRATOR_SERVICE) as Vibrator
                vib.vibrate(50)
                isQrCodeDataReceived = true
            }
        }

        override fun deviceDetected(device: ESPDevice) {
            Log.d(TAG, "Device detected")
            espDevice = device
            val deviceType = sharedPreferences.getString(
                AppConstants.KEY_DEVICE_TYPES,
                AppConstants.DEVICE_TYPE_DEFAULT
            )

            runOnUiThread {
                if (ActivityCompat.checkSelfPermission(
                        this@AddDeviceActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "Location Permission not granted.")
                    return@runOnUiThread
                }

                when (deviceType) {
                    AppConstants.DEVICE_TYPE_BLE -> {
                        if (espDevice != null && espDevice!!.transportType == ESPConstants.TransportType.TRANSPORT_SOFTAP) {
                            alertForDeviceNotSupported(getString(R.string.error_device_transport_not_supported))
                        } else {
                            device.connectToDevice()
                        }
                    }

                    AppConstants.DEVICE_TYPE_SOFTAP -> {
                        if (espDevice != null && espDevice!!.transportType == ESPConstants.TransportType.TRANSPORT_BLE) {
                            alertForDeviceNotSupported(getString(R.string.error_device_transport_not_supported))
                        } else {
                            if (espDevice!!.transportType == ESPConstants.TransportType.TRANSPORT_SOFTAP && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val wifiManager =
                                    applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                                if (!wifiManager.isWifiEnabled) {
                                    alertForWiFi()
                                    return@runOnUiThread
                                }
                            }
                            device.connectToDevice()
                        }
                    }

                    else -> {
                        if (espDevice!!.transportType == ESPConstants.TransportType.TRANSPORT_SOFTAP && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val wifiManager =
                                applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                            if (!wifiManager.isWifiEnabled) {
                                alertForWiFi()
                                return@runOnUiThread
                            }
                        }
                        device.connectToDevice()
                    }
                }
            }
        }

        override fun onFailure(e: Exception) {
            Log.e(TAG, "Error : ${e.message}")
            runOnUiThread {
                hideLoading()
                val msg = e.message
                Toast.makeText(this@AddDeviceActivity, msg, Toast.LENGTH_LONG).show()
                finish()
            }
        }

        override fun onFailure(e: Exception, qrCodeData: String) {
            Log.e(TAG, "Error : ${e.message}")
            Log.e(TAG, "QR code data : $qrCodeData")
            runOnUiThread {
                hideLoading()
                val msg = e.message
                Toast.makeText(this@AddDeviceActivity, msg, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun goToWiFiScanActivity() {
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

    private fun alertForWiFi() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setMessage(R.string.error_wifi_off)

        builder.setPositiveButton(R.string.btn_ok) { dialog, _ ->
            dialog.dismiss()
            espDevice = null
            hideLoading()
            codeScanner?.let { scanner ->
                scanner.releaseResources()
                scanner.startPreview()
                if (ActivityCompat.checkSelfPermission(
                        this@AddDeviceActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(
                        this@AddDeviceActivity,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    provisionManager.scanQRCode(codeScanner, qrCodeScanListener)
                } else {
                    Log.e(TAG, "Permissions are not granted")
                }
            }
        }
        builder.show()
    }

    private fun askForManualDeviceConnection() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(true)
        builder.setMessage("Unable to connect with device. \nDo you want to connect device manually ?")

        builder.setPositiveButton(R.string.btn_yes) { dialog, _ ->
            dialog.dismiss()
            espDevice?.let { device ->
                when (device.securityType) {
                    ESPConstants.SecurityType.SECURITY_0 -> goToWiFiProvisionLandingActivity(
                        AppConstants.SEC_TYPE_0
                    )

                    ESPConstants.SecurityType.SECURITY_1 -> goToWiFiProvisionLandingActivity(
                        AppConstants.SEC_TYPE_1
                    )

                    ESPConstants.SecurityType.SECURITY_2 -> goToWiFiProvisionLandingActivity(
                        AppConstants.SEC_TYPE_2
                    )

                    else -> goToWiFiProvisionLandingActivity(AppConstants.SEC_TYPE_2)
                }
            } ?: finish()
        }

        builder.setNegativeButton(R.string.btn_cancel) { dialog, _ ->
            dialog.dismiss()
            finish()
        }

        val alertDialog = builder.create()
        alertDialog.show()
    }

    private fun startProvisioningFlow() {
        val deviceType = sharedPreferences.getString(
            AppConstants.KEY_DEVICE_TYPES,
            AppConstants.DEVICE_TYPE_DEFAULT
        )
        val isSecure = sharedPreferences.getBoolean(AppConstants.KEY_SECURITY_TYPE, true)
        Log.d(TAG, "Device Types : $deviceType")
        var securityType = AppConstants.SEC_TYPE_0
        if (isSecure) {
            securityType = AppConstants.SEC_TYPE_DEFAULT
        }

        when (deviceType) {
            AppConstants.DEVICE_TYPE_BLE -> {
                if (isSecure) {
                    provisionManager.createESPDevice(
                        ESPConstants.TransportType.TRANSPORT_BLE,
                        ESPConstants.SecurityType.SECURITY_2
                    )
                } else {
                    provisionManager.createESPDevice(
                        ESPConstants.TransportType.TRANSPORT_BLE,
                        ESPConstants.SecurityType.SECURITY_0
                    )
                }
                goToBLEProvisionLandingActivity(securityType)
            }

            AppConstants.DEVICE_TYPE_SOFTAP -> {
                if (isSecure) {
                    provisionManager.createESPDevice(
                        ESPConstants.TransportType.TRANSPORT_SOFTAP,
                        ESPConstants.SecurityType.SECURITY_2
                    )
                } else {
                    provisionManager.createESPDevice(
                        ESPConstants.TransportType.TRANSPORT_SOFTAP,
                        ESPConstants.SecurityType.SECURITY_0
                    )
                }
                goToWiFiProvisionLandingActivity(securityType)
            }

            else -> {
                val deviceTypes = arrayOf("BLE", "SoftAP")
                val builder = AlertDialog.Builder(this)
                builder.setCancelable(true)
                builder.setTitle(R.string.dialog_msg_device_selection)
                val finalSecurityType = securityType
                builder.setItems(deviceTypes) { dialog, position ->
                    when (position) {
                        0 -> {
                            if (isSecure) {
                                provisionManager.createESPDevice(
                                    ESPConstants.TransportType.TRANSPORT_BLE,
                                    ESPConstants.SecurityType.SECURITY_2
                                )
                            } else {
                                provisionManager.createESPDevice(
                                    ESPConstants.TransportType.TRANSPORT_BLE,
                                    ESPConstants.SecurityType.SECURITY_0
                                )
                            }
                            dialog.dismiss()
                            goToBLEProvisionLandingActivity(finalSecurityType)
                        }

                        1 -> {
                            if (isSecure) {
                                provisionManager.createESPDevice(
                                    ESPConstants.TransportType.TRANSPORT_SOFTAP,
                                    ESPConstants.SecurityType.SECURITY_2
                                )
                            } else {
                                provisionManager.createESPDevice(
                                    ESPConstants.TransportType.TRANSPORT_SOFTAP,
                                    ESPConstants.SecurityType.SECURITY_0
                                )
                            }
                            dialog.dismiss()
                            goToWiFiProvisionLandingActivity(finalSecurityType)
                        }
                    }
                    dialog.dismiss()
                }
                builder.show()
            }
        }
    }

    private fun setSecurityTypeFromVersionInfo() {
        val isSecure = sharedPreferences.getBoolean(AppConstants.KEY_SECURITY_TYPE, true)
        val protoVerStr = provisionManager.espDevice?.versionInfo
        var securityType = AppConstants.SEC_TYPE_2

        try {
            val jsonObject = JSONObject(protoVerStr)
            val provInfo = jsonObject.getJSONObject("prov")

            if (provInfo.has("sec_ver")) {
                val serVer = provInfo.optInt("sec_ver")
                Log.d(TAG, "Security Version : $serVer")

                when (serVer) {
                    AppConstants.SEC_TYPE_0 -> {
                        securityType = AppConstants.SEC_TYPE_0
                        provisionManager.espDevice?.securityType =
                            ESPConstants.SecurityType.SECURITY_0
                    }

                    AppConstants.SEC_TYPE_1 -> {
                        securityType = AppConstants.SEC_TYPE_1
                        provisionManager.espDevice?.securityType =
                            ESPConstants.SecurityType.SECURITY_1
                    }

                    AppConstants.SEC_TYPE_2 -> {
                        securityType = AppConstants.SEC_TYPE_2
                        provisionManager.espDevice?.securityType =
                            ESPConstants.SecurityType.SECURITY_2
                        val deviceCaps = provisionManager.espDevice?.deviceCapabilities

                        if (deviceCaps != null && deviceCaps.size > 0 &&
                            (deviceCaps.contains(AppConstants.CAPABILITY_THREAD_SCAN) || deviceCaps.contains(
                                AppConstants.CAPABILITY_THREAD_PROV
                            ))
                        ) {
                            val userName = sharedPreferences.getString(
                                AppConstants.KEY_USER_NAME_THREAD,
                                AppConstants.DEFAULT_USER_NAME_THREAD
                            )
                            provisionManager.espDevice?.userName = userName
                        } else if (TextUtils.isEmpty(provisionManager.espDevice?.userName)) {
                            val userName = sharedPreferences.getString(
                                AppConstants.KEY_USER_NAME_WIFI,
                                AppConstants.DEFAULT_USER_NAME_WIFI
                            )
                            provisionManager.espDevice?.userName = userName
                        }
                    }

                    else -> {
                        securityType = AppConstants.SEC_TYPE_2
                        provisionManager.espDevice?.securityType =
                            ESPConstants.SecurityType.SECURITY_2
                        val deviceCaps = provisionManager.espDevice?.deviceCapabilities

                        if (deviceCaps != null && deviceCaps.size > 0 &&
                            (deviceCaps.contains(AppConstants.CAPABILITY_THREAD_SCAN) || deviceCaps.contains(
                                AppConstants.CAPABILITY_THREAD_PROV
                            ))
                        ) {
                            val userName = sharedPreferences.getString(
                                AppConstants.KEY_USER_NAME_THREAD,
                                AppConstants.DEFAULT_USER_NAME_THREAD
                            )
                            provisionManager.espDevice?.userName = userName
                        } else if (TextUtils.isEmpty(provisionManager.espDevice?.userName)) {
                            val userName = sharedPreferences.getString(
                                AppConstants.KEY_USER_NAME_WIFI,
                                AppConstants.DEFAULT_USER_NAME_WIFI
                            )
                            provisionManager.espDevice?.userName = userName
                        }
                    }
                }
            } else {
                if (securityType == AppConstants.SEC_TYPE_2) {
                    securityType = AppConstants.SEC_TYPE_1
                    provisionManager.espDevice?.securityType = ESPConstants.SecurityType.SECURITY_1
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
            Log.d(TAG, "Capabilities JSON not available.")
        }

        if (isSecure) {
            if (securityType == AppConstants.SEC_TYPE_0) {
                alertForDeviceNotSupported(getString(R.string.error_security_mismatch))
            } else {
                processDeviceCapabilities()
            }
        } else {
            if (securityType != AppConstants.SEC_TYPE_0) {
                alertForDeviceNotSupported(getString(R.string.error_security_mismatch))
            } else {
                processDeviceCapabilities()
            }
        }
    }

    private fun processDeviceCapabilities() {
        val deviceCaps = espDevice?.deviceCapabilities
        when {
            deviceCaps?.contains(AppConstants.CAPABILITY_WIFI_SCAN) == true -> goToWiFiScanActivity()
            deviceCaps?.contains(AppConstants.CAPABILITY_THREAD_SCAN) == true -> goToThreadScanActivity(
                true
            )

            deviceCaps?.contains(AppConstants.CAPABILITY_THREAD_PROV) == true -> goToThreadScanActivity(
                false
            )

            else -> goToWiFiConfigActivity()
        }
    }

    private fun goToBLEProvisionLandingActivity(securityType: Int) {
        finish()
        val bleProvisioningIntent = Intent(this@AddDeviceActivity, BLEProvisionLanding::class.java)
        bleProvisioningIntent.putExtra(AppConstants.KEY_SECURITY_TYPE, securityType)
        startActivity(bleProvisioningIntent)
    }

    private fun goToWiFiProvisionLandingActivity(securityType: Int) {
        finish()
        val wifiProvisioningIntent = Intent(applicationContext, ProvisionLanding::class.java)
        wifiProvisioningIntent.putExtra(AppConstants.KEY_SECURITY_TYPE, securityType)

        espDevice?.let { device ->
            wifiProvisioningIntent.putExtra(AppConstants.KEY_DEVICE_NAME, device.deviceName)
            wifiProvisioningIntent.putExtra(
                AppConstants.KEY_PROOF_OF_POSSESSION,
                device.proofOfPossession
            )
        }
        startActivity(wifiProvisioningIntent)
    }

    private val wifiSsid: String?
        get() {
            var ssid: String? = null
            val wifiManager =
                applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo.supplicantState == SupplicantState.COMPLETED) {
                ssid = wifiInfo.ssid
                ssid = ssid.replace("\"", "")
            }
            return ssid
        }

    private fun alertForDeviceNotSupported(msg: String) {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setTitle(R.string.error_title)
        builder.setMessage(msg)

        builder.setPositiveButton(R.string.btn_ok) { dialog, _ ->
            provisionManager.espDevice?.disconnectDevice()
            dialog.dismiss()
            finish()
        }
        builder.show()
    }
} 