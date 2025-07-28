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
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import com.espressif.AppConstants
import com.espressif.provisioning.DeviceConnectionEvent
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.listeners.BleScanListener
import com.espressif.ui.adapters.BleDeviceListAdapter
import com.espressif.ui.models.BleDevice
import com.espressif.ui.utils.Utils
import com.espressif.wifi_provisioning.BuildConfig
import com.espressif.wifi_provisioning.R
import com.espressif.wifi_provisioning.databinding.ActivityBleprovisionLandingBinding
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class BLEProvisionLanding : ManualProvBaseActivity() {

    companion object {

        private val TAG: String = BLEProvisionLanding::class.java.simpleName

        // Request codes
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_FINE_LOCATION = 2

        // Time out
        private const val DEVICE_CONNECT_TIMEOUT: Long = 20000
    }

    private lateinit var binding: ActivityBleprovisionLandingBinding

    private lateinit var deviceList: ArrayList<BleDevice>

    //    public static boolean isBleWorkDone = false;

    private var adapter: BleDeviceListAdapter? = null
    private var bleAdapter: BluetoothAdapter? = null
    private var bluetoothDevices: HashMap<BluetoothDevice, String>? = null
    private var handler: Handler? = null

    private var position = -1
    private var deviceNamePrefix: String? = null
    private var isDeviceConnected = false
    private var isConnecting = false
    private var isScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBleprovisionLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.error_ble_not_supported, Toast.LENGTH_SHORT).show()
            finish()
        }

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bleAdapter = bluetoothManager.adapter

        // Checks if Bluetooth is supported on the device.
        if (bleAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        isConnecting = false
        isDeviceConnected = false
        handler = Handler()
        bluetoothDevices = HashMap()
        deviceList = ArrayList()
        deviceNamePrefix = sharedPreferences?.getString(
            AppConstants.KEY_BLE_DEVICE_NAME_PREFIX,
            resources.getString(R.string.ble_device_name_prefix)
        )
        initViews()
    }

    override fun onResume() {
        super.onResume()

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!bleAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this@BLEProvisionLanding,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                } else {
                    Log.e(TAG, "BLUETOOTH_CONNECT permission is not granted.")
                    return
                }
            } else {
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        } else {
            if (!isDeviceConnected && !isConnecting) {
                startScan()
            }
        }
    }

    override fun onBackPressed() {
        if (isScanning) {
            stopScan()
        }
        super.onBackPressed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult, requestCode : $requestCode, resultCode : $resultCode")

        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_CANCELED) {
            finish()
            return
        }

        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            startScan()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_FINE_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startScan()
                } else if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    finish()
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(event: DeviceConnectionEvent) {
        Log.d(TAG, "ON Device Prov Event RECEIVED : " + event.eventType)
        handler!!.removeCallbacks(disconnectDeviceTask)

        when (event.eventType) {
            ESPConstants.EVENT_DEVICE_CONNECTED -> {
                Log.d(TAG, "Device Connected Event Received")
                binding.layoutBleProvisioning.bleLandingProgressIndicator.visibility = View.GONE
                isConnecting = false
                isDeviceConnected = true
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

            ESPConstants.EVENT_DEVICE_DISCONNECTED -> {
                binding.layoutBleProvisioning.bleLandingProgressIndicator.visibility = View.GONE
                isConnecting = false
                isDeviceConnected = false
                Toast.makeText(this@BLEProvisionLanding, "Device disconnected", Toast.LENGTH_LONG)
                    .show()
            }

            ESPConstants.EVENT_DEVICE_CONNECTION_FAILED -> {
                binding.layoutBleProvisioning.bleLandingProgressIndicator.visibility = View.GONE
                isConnecting = false
                isDeviceConnected = false
                Utils.displayDeviceConnectionError(
                    this,
                    getString(R.string.error_device_connect_failed)
                )
            }
        }
    }

    private fun initViews() {
        setToolbar()

        // Set visibility of Prefix layout
        if (BuildConfig.isFilteringByPrefixAllowed) {
            binding.layoutBleProvisioning.prefixLayout.visibility = View.VISIBLE
        } else {
            binding.layoutBleProvisioning.prefixLayout.visibility = View.GONE
        }
        binding.layoutBleProvisioning.prefixValue.text = deviceNamePrefix

        // Set adapter for device list
        adapter = BleDeviceListAdapter(this, R.layout.item_ble_scan, deviceList)
        binding.layoutBleProvisioning.bleDevicesList.setAdapter(adapter)
        binding.layoutBleProvisioning.bleDevicesList.setOnItemClickListener(onDeviceCLickListener)
        binding.layoutBleProvisioning.btnScan.setOnClickListener(btnScanClickListener)
        binding.layoutBleProvisioning.btnChangePrefix.setOnClickListener(
            btnPrefixChangeClickListener
        )
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

    private fun hasPermissions(): Boolean {
        if (bleAdapter == null || !bleAdapter!!.isEnabled) {
            requestBluetoothEnable()
            return false
        } else if (!hasLocationAndBtPermissions()) {
            requestLocationAndBtPermission()
            return false
        }
        return true
    }

    private fun requestBluetoothEnable() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            } else {
                requestLocationAndBtPermission()
            }
        } else {
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }

    private fun hasLocationAndBtPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissionsGranted =
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            return permissionsGranted
        } else {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestLocationAndBtPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ), REQUEST_FINE_LOCATION
            )
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_FINE_LOCATION
            )
        }
    }

    private fun processDeviceCapabilities() {
        val deviceCaps = provisionManager!!.espDevice.deviceCapabilities

        if (deviceCaps != null) {
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
        } else {
            goToWiFiConfigActivity()
        }
    }

    private fun startScan() {
        if (!hasPermissions() || isScanning) {
            return
        }

        isScanning = true
        deviceList!!.clear()
        bluetoothDevices!!.clear()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            provisionManager!!.searchBleEspDevices(deviceNamePrefix, bleScanListener)
            updateProgressAndScanBtn()
        } else {
            Log.e(TAG, "Not able to start scan as Location permission is not granted.")
            Toast.makeText(
                this@BLEProvisionLanding,
                "Please give location permission to start BLE scan",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopScan() {
        isScanning = false

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            provisionManager!!.stopBleScan()
            updateProgressAndScanBtn()
        } else {
            Log.e(TAG, "Not able to stop scan as Location permission is not granted.")
            Toast.makeText(
                this@BLEProvisionLanding,
                "Please give location permission to stop BLE scan",
                Toast.LENGTH_LONG
            ).show()
        }

        if (deviceList!!.size <= 0) {
            Toast.makeText(
                this@BLEProvisionLanding,
                R.string.error_no_ble_device,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * This method will update UI (Scan button enable / disable and progressbar visibility)
     */
    private fun updateProgressAndScanBtn() {
        if (isScanning) {
            binding.layoutBleProvisioning.btnScan.isEnabled = false
            binding.layoutBleProvisioning.btnScan.alpha = 0.5f
            binding.layoutBleProvisioning.btnScan.setTextColor(Color.WHITE)
            binding.layoutBleProvisioning.bleLandingProgressIndicator.visibility = View.VISIBLE
            binding.layoutBleProvisioning.bleDevicesList.visibility = View.GONE
        } else {
            binding.layoutBleProvisioning.btnScan.isEnabled = true
            binding.layoutBleProvisioning.btnScan.alpha = 1f
            binding.layoutBleProvisioning.bleLandingProgressIndicator.visibility = View.GONE
            binding.layoutBleProvisioning.bleDevicesList.visibility = View.VISIBLE
        }
    }

    private val btnScanClickListener = View.OnClickListener {
        bluetoothDevices!!.clear()
        adapter!!.clear()
        startScan()
    }

    private val btnPrefixChangeClickListener = View.OnClickListener { askForPrefix() }

    private val bleScanListener: BleScanListener = object : BleScanListener {
        override fun scanStartFailed() {
            Toast.makeText(
                this@BLEProvisionLanding,
                "Please turn on Bluetooth to connect BLE device",
                Toast.LENGTH_SHORT
            ).show()
        }

        override fun onPeripheralFound(device: BluetoothDevice, scanResult: ScanResult) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(
                        this@BLEProvisionLanding,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d(TAG, "====== onPeripheralFound ===== " + device.name)
                }
            } else {
                Log.d(TAG, "====== onPeripheralFound ===== " + device.name)
            }

            var deviceExists = false
            var serviceUuid = ""

            if (scanResult.scanRecord!!.serviceUuids != null && scanResult.scanRecord!!.serviceUuids.size > 0) {
                serviceUuid = scanResult.scanRecord!!.serviceUuids[0].toString()
            }
            Log.d(TAG, "Add service UUID : $serviceUuid")

            if (bluetoothDevices!!.containsKey(device)) {
                deviceExists = true
            }

            if (!deviceExists) {
                val bleDevice = BleDevice(scanResult.scanRecord!!.deviceName, device)
                binding.layoutBleProvisioning.bleDevicesList.visibility = View.VISIBLE
                bluetoothDevices!![device] = serviceUuid
                deviceList!!.add(bleDevice)
                adapter!!.notifyDataSetChanged()
            }
        }

        override fun scanCompleted() {
            isScanning = false
            updateProgressAndScanBtn()
        }

        override fun onFailure(e: Exception) {
            Log.e(TAG, e.message!!)
            e.printStackTrace()
        }
    }

    private val onDeviceCLickListener =
        OnItemClickListener { adapterView, view, position, l ->
            stopScan()
            isConnecting = true
            isDeviceConnected = false
            binding.layoutBleProvisioning.btnScan.visibility = View.GONE
            binding.layoutBleProvisioning.bleDevicesList.visibility = View.GONE
            binding.layoutBleProvisioning.bleLandingProgressIndicator.visibility = View.VISIBLE
            this@BLEProvisionLanding.position = position
            val bleDevice = adapter!!.getItem(position)
            val uuid = bluetoothDevices!![bleDevice!!.bluetoothDevice]
            Log.d(
                TAG,
                "=================== Connect to device : " + bleDevice.name + " UUID : " + uuid
            )
            if (ActivityCompat.checkSelfPermission(
                    this@BLEProvisionLanding,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                provisionManager?.espDevice!!.connectBLEDevice(bleDevice.bluetoothDevice, uuid)
                handler!!.postDelayed(disconnectDeviceTask, DEVICE_CONNECT_TIMEOUT)
            } else {
                Log.e(TAG, "Not able to connect device as Location permission is not granted.")
                Toast.makeText(
                    this@BLEProvisionLanding,
                    "Please give location permission to connect device",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val disconnectDeviceTask = Runnable {
        Log.e(TAG, "Disconnect device")
        binding.layoutBleProvisioning.bleLandingProgressIndicator.visibility = View.GONE
        Utils.displayDeviceConnectionError(
            this@BLEProvisionLanding,
            getString(R.string.error_device_not_supported)
        )
    }

    private fun askForPrefix() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(true)

        val layoutInflaterAndroid = LayoutInflater.from(this)
        val view = layoutInflaterAndroid.inflate(R.layout.dialog_prefix, null)
        builder.setView(view)
        val etPrefix = view.findViewById<EditText>(R.id.et_prefix)
        etPrefix.setText(deviceNamePrefix)
        etPrefix.setSelection(etPrefix.text.length)

        // Set up the buttons
        builder.setPositiveButton(R.string.btn_save) { dialog, which ->
            var prefix = etPrefix.text.toString()
            if (prefix != null) {
                prefix = prefix.trim { it <= ' ' }
            }

            val editor = sharedPreferences!!.edit()
            editor.putString(AppConstants.KEY_BLE_DEVICE_NAME_PREFIX, prefix)
            editor.apply()
            deviceNamePrefix = prefix
            binding.layoutBleProvisioning.prefixValue.text = prefix
            startScan()
        }

        builder.setNegativeButton(R.string.btn_cancel) { dialog, which -> }
        builder.show()
    }

    private fun goToPopActivity() {
        finish()
        val popIntent = Intent(applicationContext, ProofOfPossessionActivity::class.java)
        popIntent.putExtra(AppConstants.KEY_DEVICE_NAME, deviceList!![position].name)
        startActivity(popIntent)
    }

    private fun goToWifiScanListActivity() {
        finish()
        val wifiListIntent = Intent(applicationContext, WiFiScanActivity::class.java)
        wifiListIntent.putExtra(AppConstants.KEY_DEVICE_NAME, deviceList!![position].name)
        startActivity(wifiListIntent)
    }

    private fun goToThreadScanActivity(scanCapAvailable: Boolean) {
        finish()
        val threadConfigIntent = Intent(applicationContext, ThreadConfigActivity::class.java)
        threadConfigIntent.putExtra(AppConstants.KEY_DEVICE_NAME, deviceList!![position].name)
        threadConfigIntent.putExtra(AppConstants.KEY_THREAD_SCAN_AVAILABLE, scanCapAvailable)
        startActivity(threadConfigIntent)
    }

    private fun goToWiFiConfigActivity() {
        finish()
        val wifiConfigIntent = Intent(applicationContext, WiFiConfigActivity::class.java)
        startActivity(wifiConfigIntent)
    }
}
