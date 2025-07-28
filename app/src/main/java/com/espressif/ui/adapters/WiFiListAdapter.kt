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
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.espressif.provisioning.ESPConstants
import com.espressif.provisioning.WiFiAccessPoint
import com.espressif.wifi_provisioning.R

class WiFiListAdapter(
    private val context: Context,
    resource: Int,
    private val wifiApList: ArrayList<WiFiAccessPoint>
) :
    ArrayAdapter<WiFiAccessPoint>(context, resource, wifiApList) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val wiFiAccessPoint = wifiApList[position]

        //get the inflater and inflate the XML layout for each item
        val inflater = context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.item_wifi_access_point, null)

        val wifiNameText = view.findViewById<TextView>(R.id.tv_wifi_name)
        val rssiImage = view.findViewById<ImageView>(R.id.iv_wifi_rssi)
        val lockImage = view.findViewById<ImageView>(R.id.iv_wifi_security)

        wifiNameText.text = wiFiAccessPoint.wifiName
        rssiImage.setImageLevel(getRssiLevel(wiFiAccessPoint.rssi))

        if (wiFiAccessPoint.security == ESPConstants.WIFI_OPEN.toInt()) {
            lockImage.visibility = View.GONE
        } else {
            lockImage.visibility = View.VISIBLE
        }

        if (wiFiAccessPoint.wifiName == context.getString(R.string.join_other_network)) {
            wifiNameText.setTextColor(
                ContextCompat.getColor(
                    context.applicationContext,
                    R.color.colorPrimary
                )
            )
            rssiImage.visibility = View.VISIBLE
            rssiImage.setImageResource(R.drawable.ic_right_arrow)
        }

        return view
    }

    private fun getRssiLevel(rssiValue: Int): Int {
        return if (rssiValue > -50) {
            3
        } else if (rssiValue >= -60) {
            2
        } else if (rssiValue >= -67) {
            1
        } else {
            0
        }
    }
}
