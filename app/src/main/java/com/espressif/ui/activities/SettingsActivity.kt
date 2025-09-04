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

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.espressif.AppConstants
import com.espressif.wifi_provisioning.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings, SettingsFragment())
            .commit()
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)

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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Note : Overriding this method to make ActionBar "Back" button working.

        val id = item.itemId
        if (id == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        private var securityPref: SwitchPreferenceCompat? = null
        private var userNamePrefWifi: EditTextPreference? = null
        private var userNamePrefThread: EditTextPreference? = null

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val prefMgr = preferenceManager
            prefMgr.sharedPreferencesName = AppConstants.ESP_PREFERENCES
            setPreferencesFromResource(R.xml.preferences, rootKey)

            val sharedPreferences = prefMgr.sharedPreferences
            securityPref = prefMgr.findPreference(AppConstants.KEY_SECURITY_TYPE)
            userNamePrefWifi = prefMgr.findPreference(AppConstants.KEY_USER_NAME_WIFI)
            userNamePrefThread = prefMgr.findPreference(AppConstants.KEY_USER_NAME_THREAD)

            val isSecure = sharedPreferences!!.getBoolean(AppConstants.KEY_SECURITY_TYPE, true)
            if (isSecure) {
                securityPref!!.setSummary(R.string.summary_secured)
                userNamePrefWifi!!.isVisible = true
                userNamePrefThread!!.isVisible = true
            } else {
                securityPref!!.setSummary(R.string.summary_unsecured)
                userNamePrefWifi!!.isVisible = false
                userNamePrefThread!!.isVisible = false
            }

            securityPref!!.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference, newValue ->
                    val isSecure = newValue as Boolean
                    if (isSecure) {
                        preference.setSummary(R.string.summary_secured)
                        userNamePrefWifi!!.isVisible = true
                        userNamePrefThread!!.isVisible = true
                    } else {
                        preference.setSummary(R.string.summary_unsecured)
                        userNamePrefWifi!!.isVisible = false
                        userNamePrefThread!!.isVisible = false
                    }
                    true
                }
        }
    }
}
