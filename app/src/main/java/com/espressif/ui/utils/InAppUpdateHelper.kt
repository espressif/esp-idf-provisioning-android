// Copyright 2026 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.ui.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.espressif.AppConstants
import com.espressif.wifi_provisioning.BuildConfig
import com.espressif.wifi_provisioning.R
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import java.util.concurrent.TimeUnit

/** Eligibility rules for the flexible update prompt. No Android types, so they run as plain JVM unit tests. */
internal object UpdatePolicy {

    /** Minimum gap before asking again about the same version. A newer version is offered right away. */
    val MIN_INTERVAL_MS: Long = TimeUnit.DAYS.toMillis(7)

    /** A flexible update is already pending, downloading, downloaded or installing. */
    private val IN_PROGRESS_STATUSES = setOf(
        InstallStatus.PENDING,
        InstallStatus.DOWNLOADING,
        InstallStatus.DOWNLOADED,
        InstallStatus.INSTALLING
    )

    fun canStartFlexibleUpdate(updateAvailability: Int, installStatus: Int, isFlexibleAllowed: Boolean): Boolean =
        updateAvailability == UpdateAvailability.UPDATE_AVAILABLE &&
                isFlexibleAllowed &&
                installStatus !in IN_PROGRESS_STATUSES

    fun shouldPrompt(
        availableVersionCode: Int,
        lastPromptVersionCode: Int,
        lastPromptAtMs: Long,
        nowMs: Long
    ): Boolean =
        availableVersionCode > lastPromptVersionCode ||
                lastPromptAtMs <= 0L ||
                nowMs - lastPromptAtMs >= MIN_INTERVAL_MS

    /**
     * The user accepted [acceptedVersionCode], yet that version is still on offer and nothing is downloading or
     * downloaded: the download failed or was cancelled. Play reports FAILED/CANCELED only transiently (to install
     * state listeners, then back to UNKNOWN), so this is inferred from the state at the next check.
     */
    fun isAcceptedDownloadLost(
        acceptedVersionCode: Int,
        availableVersionCode: Int,
        updateAvailability: Int,
        installStatus: Int
    ): Boolean =
        acceptedVersionCode != 0 &&
                acceptedVersionCode == availableVersionCode &&
                updateAvailability == UpdateAvailability.UPDATE_AVAILABLE &&
                installStatus !in IN_PROGRESS_STATUSES

    /** Clock moved backwards past the last prompt: restart the interval from now (never shortens it). */
    fun normalizeLastPrompt(lastPromptAtMs: Long, nowMs: Long): Long =
        if (lastPromptAtMs > nowMs) nowMs else lastPromptAtMs
}

/**
 * Google Play In-App Updates, flexible flow.
 *
 * - Attached to the home screen only, so the Play update sheet never covers a provisioning step.
 * - Update availability is checked once per app launch. The download runs in the background while
 *   the user keeps using the app (including provisioning).
 * - Once downloaded, the home screen shows a "Restart" Snackbar and the update is installed only when
 *   the user taps it. completeUpdate() is never called automatically: the app goes to the background
 *   mid-provisioning (e.g. to join the device's SoftAP in Wi-Fi settings) and an install would kill it.
 * - Works only for apps installed from Google Play; for other installs the check fails silently.
 */
class InAppUpdateHelper private constructor(
    private val activity: AppCompatActivity,
    private val snackbarParent: View
) : DefaultLifecycleObserver {

    companion object {
        private val TAG: String = InAppUpdateHelper::class.java.simpleName

        /** Process-scoped. Deliberately not persisted, so every cold start checks once. */
        @Volatile
        private var promptCheckedThisLaunch = false

        /** Call from the home screen's onCreate, after setContentView. No-op when the feature is disabled. */
        fun attach(activity: AppCompatActivity, snackbarParent: View) {
            if (!BuildConfig.isInAppUpdateEnabled) return
            activity.lifecycle.addObserver(InAppUpdateHelper(activity, snackbarParent))
        }
    }

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity.applicationContext)
    private val prefs: SharedPreferences =
        activity.applicationContext.getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE)
    private var restartSnackbar: Snackbar? = null

    // Registered here, before the activity is STARTED, so a result is still delivered after recreation.
    private val updateFlowLauncher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    Log.d(TAG, "Flexible update accepted, downloading in background")
                    // Lets a download that later fails re-offer this version once, instead of waiting out the interval.
                    prefs.edit().putInt(
                        AppConstants.KEY_UPDATE_ACCEPTED_VERSION,
                        prefs.getInt(AppConstants.KEY_UPDATE_LAST_PROMPT_VERSION, 0)
                    ).apply()
                }
                Activity.RESULT_CANCELED -> Log.d(TAG, "Flexible update declined")
                ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> {
                    // Not the user's choice: allow another prompt on the next launch.
                    Log.w(TAG, "Flexible update flow failed")
                    clearLastPrompt()
                }
            }
        }

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> showRestartSnackbar()
            InstallStatus.FAILED -> Log.w(TAG, "Update download failed, errorCode=${state.installErrorCode()}")
            else -> Unit
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        appUpdateManager.registerListener(installStateListener)
    }

    override fun onResume(owner: LifecycleOwner) {
        // Activity-scoped listeners: removed automatically in onStop, so a late result never
        // reaches a screen the user has already left.
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener(activity) { info -> onAppUpdateInfo(info) }
            .addOnFailureListener(activity) { e ->
                // Not installed from Google Play, Play Store missing, offline (e.g. joined to a device's SoftAP), etc.
                Log.d(TAG, "Update check failed, errorCode=${(e as? InstallException)?.errorCode}")
            }
    }

    override fun onStop(owner: LifecycleOwner) {
        appUpdateManager.unregisterListener(installStateListener)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        restartSnackbar = null
    }

    private fun onAppUpdateInfo(info: AppUpdateInfo) {
        if (info.installStatus() == InstallStatus.DOWNLOADED) {
            showRestartSnackbar()
            return
        }
        // Consumed only by a result that reached the resumed home screen, whatever the outcome. A result that
        // arrives after the user left is retried on the next resume. The persisted prompt record (written before
        // launching) keeps the Play sheet's own pause/resume from prompting again.
        if (promptCheckedThisLaunch || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        promptCheckedThisLaunch = true

        if (UpdatePolicy.isAcceptedDownloadLost(
                prefs.getInt(AppConstants.KEY_UPDATE_ACCEPTED_VERSION, 0),
                info.availableVersionCode(),
                info.updateAvailability(),
                info.installStatus()
            )
        ) {
            // Re-offered once: clearing also drops the accepted marker, so a decline starts the normal interval.
            Log.d(TAG, "Accepted update ${info.availableVersionCode()} did not finish downloading")
            clearLastPrompt()
        }

        val options = AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE)
        if (!UpdatePolicy.canStartFlexibleUpdate(
                info.updateAvailability(),
                info.installStatus(),
                info.isUpdateTypeAllowed(options)
            )
        ) {
            return
        }

        val now = System.currentTimeMillis()
        val storedLast = prefs.getLong(AppConstants.KEY_UPDATE_LAST_PROMPT_TIME, 0L)
        val last = UpdatePolicy.normalizeLastPrompt(storedLast, now)
        if (last != storedLast) {
            prefs.edit().putLong(AppConstants.KEY_UPDATE_LAST_PROMPT_TIME, last).apply()
        }
        val lastVersion = prefs.getInt(AppConstants.KEY_UPDATE_LAST_PROMPT_VERSION, 0)
        val availableVersion = info.availableVersionCode()
        if (!UpdatePolicy.shouldPrompt(availableVersion, lastVersion, last, now)) {
            Log.d(TAG, "Update $availableVersion already offered recently")
            return
        }

        // Recorded before launching: the result may reach a recreated activity after process death.
        prefs.edit()
            .putInt(AppConstants.KEY_UPDATE_LAST_PROMPT_VERSION, availableVersion)
            .putLong(AppConstants.KEY_UPDATE_LAST_PROMPT_TIME, now)
            .remove(AppConstants.KEY_UPDATE_ACCEPTED_VERSION)
            .apply()

        val started = try {
            appUpdateManager.startUpdateFlowForResult(info, updateFlowLauncher, options)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start flexible update flow", e)
            false
        }
        if (!started) {
            clearLastPrompt()
        }
    }

    private fun showRestartSnackbar() {
        if (activity.isFinishing || activity.isDestroyed) return
        if (restartSnackbar?.isShownOrQueued == true) return

        restartSnackbar = Snackbar.make(snackbarParent, R.string.update_downloaded, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.btn_restart) {
                appUpdateManager.completeUpdate().addOnFailureListener(activity) { e ->
                    Log.e(TAG, "Unable to complete update", e)
                    // The action dismissed the Snackbar; offer it again while the update is still downloaded.
                    appUpdateManager.appUpdateInfo.addOnSuccessListener(activity) { info ->
                        if (info.installStatus() == InstallStatus.DOWNLOADED) {
                            // The dismissed one may still be animating out and would block a re-show.
                            restartSnackbar = null
                            showRestartSnackbar()
                        }
                    }
                }
            }
            .also { it.show() }
    }

    private fun clearLastPrompt() {
        prefs.edit()
            .remove(AppConstants.KEY_UPDATE_LAST_PROMPT_VERSION)
            .remove(AppConstants.KEY_UPDATE_LAST_PROMPT_TIME)
            .remove(AppConstants.KEY_UPDATE_ACCEPTED_VERSION)
            .apply()
    }
}
