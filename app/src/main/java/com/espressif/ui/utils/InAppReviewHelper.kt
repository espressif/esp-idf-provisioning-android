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
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import com.espressif.AppConstants
import com.espressif.wifi_provisioning.BuildConfig
import com.google.android.play.core.review.ReviewException
import com.google.android.play.core.review.ReviewManagerFactory
import java.util.concurrent.TimeUnit

/** Eligibility rules for the in-app review prompt. No Android types, so they run as plain JVM unit tests. */
internal object ReviewPolicy {

    /** Successful provisionings required before (and between) review requests. */
    const val MIN_SUCCESSFUL_PROVISIONS = 2

    /** Minimum gap between two review requests, on top of Google Play's own quota. */
    val MIN_INTERVAL_MS: Long = TimeUnit.DAYS.toMillis(90)

    fun isEligible(successCount: Int, lastPromptAtMs: Long, nowMs: Long): Boolean =
        successCount >= MIN_SUCCESSFUL_PROVISIONS &&
                (lastPromptAtMs <= 0L || nowMs - lastPromptAtMs >= MIN_INTERVAL_MS)

    /** Clock moved backwards past the last prompt: restart the cooldown from now (never shortens it). */
    fun normalizeLastPrompt(lastPromptAtMs: Long, nowMs: Long): Long =
        if (lastPromptAtMs > nowMs) nowMs else lastPromptAtMs
}

/**
 * Google Play In-App Review.
 *
 * - The review card is requested only after the user returns to the home screen following a
 *   clean, successful provisioning (natural break, device already disconnected).
 * - Google Play decides whether the card is actually shown (only for apps installed from Google Play);
 *   the app never learns the outcome and continues identically in every case.
 * - "Rate this app" in Settings opens the Play Store listing; it never calls the review API.
 */
object InAppReviewHelper {

    private val TAG: String = InAppReviewHelper::class.java.simpleName
    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    /** Lets the home screen settle (and, after SoftAP, the phone rejoin its Wi-Fi) before asking. */
    const val LAUNCH_DELAY_MS = 1_000L

    /** One-shot and process-scoped. Deliberately not persisted, so it never fires on an unrelated cold start. */
    @Volatile
    private var pendingAfterSuccess = false

    /** Review prompt usable on this device: flag is on and Google Play services are available. */
    fun isAvailable(context: Context): Boolean =
        BuildConfig.isInAppReviewEnabled && Utils.isPlayServicesAvailable(context)

    /** "Rate this app" is shown only for Play installs, where the app's store listing exists. */
    fun canOpenStoreListing(context: Context): Boolean =
        BuildConfig.isInAppReviewEnabled && isInstalledFromPlayStore(context)

    /** Call on the UI thread, once per successful provisioning. Never shows UI. */
    fun onProvisioningSuccess(context: Context, cleanRun: Boolean) {
        if (!isAvailable(context)) return
        val prefs = prefs(context)
        val count = prefs.getInt(AppConstants.KEY_REVIEW_PROV_SUCCESS_COUNT, 0)
        prefs.edit().putInt(AppConstants.KEY_REVIEW_PROV_SUCCESS_COUNT, count + 1).apply()
        pendingAfterSuccess = cleanRun
    }

    fun hasPendingPrompt(): Boolean = pendingAfterSuccess

    /** A new provisioning attempt supersedes any prompt still pending from the previous one. */
    fun cancelPendingPrompt() {
        pendingAfterSuccess = false
    }

    /** Called from EspMainActivity, LAUNCH_DELAY_MS after onResume (cancelled in onPause). */
    fun maybeLaunch(activity: AppCompatActivity) {
        if (!pendingAfterSuccess) return
        // Consume first: the review card pauses/resumes the host, which must not re-trigger this.
        pendingAfterSuccess = false

        val appContext = activity.applicationContext
        if (!isAvailable(appContext)) return

        val prefs = prefs(appContext)
        val now = System.currentTimeMillis()
        val storedLast = prefs.getLong(AppConstants.KEY_REVIEW_LAST_PROMPT_TIME, 0L)
        val last = ReviewPolicy.normalizeLastPrompt(storedLast, now)
        if (last != storedLast) {
            prefs.edit().putLong(AppConstants.KEY_REVIEW_LAST_PROMPT_TIME, last).apply()
        }
        val count = prefs.getInt(AppConstants.KEY_REVIEW_PROV_SUCCESS_COUNT, 0)
        if (!ReviewPolicy.isEligible(count, last, now)) {
            Log.d(TAG, "Not eligible for review prompt (count=$count)")
            return
        }

        val manager = ReviewManagerFactory.create(appContext)

        // Activity-scoped listener: removed automatically in onStop, so a late result never
        // reaches a screen the user has already left.
        manager.requestReviewFlow().addOnCompleteListener(activity) { request ->
            if (!request.isSuccessful) {
                // Offline, Play Store missing/outdated, etc. Stay silent and do not start the cooldown.
                val code = (request.exception as? ReviewException)?.errorCode
                Log.w(TAG, "requestReviewFlow failed, errorCode=$code", request.exception)
                return@addOnCompleteListener
            }
            if (activity.isFinishing || activity.isDestroyed ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            ) {
                return@addOnCompleteListener
            }

            // Play never reports whether the card was shown, so record the attempt now.
            prefs.edit()
                .putLong(AppConstants.KEY_REVIEW_LAST_PROMPT_TIME, System.currentTimeMillis())
                .putInt(AppConstants.KEY_REVIEW_PROV_SUCCESS_COUNT, 0)
                .apply()

            try {
                manager.launchReviewFlow(activity, request.result).addOnCompleteListener {
                    // Per Play guidelines: no follow-up UI and no branching on the outcome.
                    Log.d(TAG, "In-app review flow finished")
                }
            } catch (e: Exception) {
                Log.e(TAG, "launchReviewFlow failed", e)
            }
        }
    }

    /** Settings "Rate this app": opens the Play Store listing. Never the in-app review API. */
    fun openStoreListing(activity: Activity) {
        val pkg = activity.packageName
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                    .setPackage(PLAY_STORE_PACKAGE)
            )
        } catch (e: ActivityNotFoundException) {
            try {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                )
            } catch (e2: ActivityNotFoundException) {
                Log.e(TAG, "No app available to open the store listing", e2)
            }
        }
    }

    private fun isInstalledFromPlayStore(context: Context): Boolean = try {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
        installer == PLAY_STORE_PACKAGE
    } catch (e: Exception) {
        Log.w(TAG, "Unable to get installer package", e)
        false
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE)
}
