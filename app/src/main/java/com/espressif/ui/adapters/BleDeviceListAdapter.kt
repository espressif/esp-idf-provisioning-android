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

package com.espressif.ui.adapters

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.espressif.ui.models.BleDevice
import com.espressif.wifi_provisioning.R

class BleDeviceListAdapter(
    private val context: Context,
    resource: Int,
    private val bluetoothDevices: ArrayList<BleDevice>
) :
    ArrayAdapter<BleDevice>(context, resource, bluetoothDevices) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val btDevice = bluetoothDevices[position]

        //get the inflater and inflate the XML layout for each item
        val inflater = context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.item_ble_scan, null)

        val bleDeviceNameText = view.findViewById<TextView>(R.id.tv_ble_device_name)
        bleDeviceNameText.text = btDevice.name

        return view
    }
}
