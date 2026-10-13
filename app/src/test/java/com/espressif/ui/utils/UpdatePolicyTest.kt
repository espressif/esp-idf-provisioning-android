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

import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    private val now = 1_800_000_000_000L
    private val interval = UpdatePolicy.MIN_INTERVAL_MS

    @Test
    fun updateAvailable_idle_canStart() {
        assertTrue(UpdatePolicy.canStartFlexibleUpdate(UpdateAvailability.UPDATE_AVAILABLE, InstallStatus.UNKNOWN, true))
    }

    @Test
    fun noUpdateAvailable_cannotStart() {
        assertFalse(UpdatePolicy.canStartFlexibleUpdate(UpdateAvailability.UPDATE_NOT_AVAILABLE, InstallStatus.UNKNOWN, true))
        assertFalse(UpdatePolicy.canStartFlexibleUpdate(UpdateAvailability.UNKNOWN, InstallStatus.UNKNOWN, true))
        assertFalse(
            UpdatePolicy.canStartFlexibleUpdate(
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS, InstallStatus.UNKNOWN, true
            )
        )
    }

    @Test
    fun flexibleNotAllowed_cannotStart() {
        assertFalse(UpdatePolicy.canStartFlexibleUpdate(UpdateAvailability.UPDATE_AVAILABLE, InstallStatus.UNKNOWN, false))
    }

    @Test
    fun updateInProgress_cannotStart() {
        for (status in listOf(
            InstallStatus.PENDING,
            InstallStatus.DOWNLOADING,
            InstallStatus.DOWNLOADED,
            InstallStatus.INSTALLING
        )) {
            assertFalse(UpdatePolicy.canStartFlexibleUpdate(UpdateAvailability.UPDATE_AVAILABLE, status, true))
        }
    }

    @Test
    fun previousAttemptEnded_canStartAgain() {
        for (status in listOf(InstallStatus.FAILED, InstallStatus.CANCELED, InstallStatus.INSTALLED)) {
            assertTrue(UpdatePolicy.canStartFlexibleUpdate(UpdateAvailability.UPDATE_AVAILABLE, status, true))
        }
    }

    @Test
    fun neverPrompted_shouldPrompt() {
        assertTrue(UpdatePolicy.shouldPrompt(40, 0, 0L, now))
    }

    @Test
    fun sameVersion_intervalBoundary() {
        assertFalse(UpdatePolicy.shouldPrompt(40, 40, now - interval + 1, now))
        assertTrue(UpdatePolicy.shouldPrompt(40, 40, now - interval, now))
    }

    @Test
    fun newerVersion_promptsWithinInterval() {
        assertTrue(UpdatePolicy.shouldPrompt(41, 40, now - 1_000L, now))
    }

    @Test
    fun olderOrSameVersion_waitsForInterval() {
        assertFalse(UpdatePolicy.shouldPrompt(39, 40, now - 1_000L, now))
        assertFalse(UpdatePolicy.shouldPrompt(40, 40, now - 1_000L, now))
    }

    @Test
    fun lastPromptInFuture_notPrompted() {
        assertFalse(UpdatePolicy.shouldPrompt(40, 40, now + 1_000L, now))
    }

    @Test
    fun acceptedVersionStillOffered_notDownloading_isLost() {
        for (status in listOf(
            InstallStatus.UNKNOWN,
            InstallStatus.FAILED,
            InstallStatus.CANCELED
        )) {
            assertTrue(UpdatePolicy.isAcceptedDownloadLost(40, 40, UpdateAvailability.UPDATE_AVAILABLE, status))
        }
    }

    @Test
    fun acceptedDownloadInProgressOrDone_notLost() {
        for (status in listOf(
            InstallStatus.PENDING,
            InstallStatus.DOWNLOADING,
            InstallStatus.DOWNLOADED,
            InstallStatus.INSTALLING
        )) {
            assertFalse(UpdatePolicy.isAcceptedDownloadLost(40, 40, UpdateAvailability.UPDATE_AVAILABLE, status))
        }
        assertFalse(
            UpdatePolicy.isAcceptedDownloadLost(
                40, 40, UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS, InstallStatus.UNKNOWN
            )
        )
    }

    @Test
    fun notAccepted_orOtherVersion_orNoUpdate_notLost() {
        assertFalse(UpdatePolicy.isAcceptedDownloadLost(0, 40, UpdateAvailability.UPDATE_AVAILABLE, InstallStatus.UNKNOWN))
        assertFalse(UpdatePolicy.isAcceptedDownloadLost(40, 41, UpdateAvailability.UPDATE_AVAILABLE, InstallStatus.UNKNOWN))
        assertFalse(
            UpdatePolicy.isAcceptedDownloadLost(40, 40, UpdateAvailability.UPDATE_NOT_AVAILABLE, InstallStatus.UNKNOWN)
        )
    }

    @Test
    fun normalize_clampsOnlyFutureValues() {
        assertEquals(now, UpdatePolicy.normalizeLastPrompt(now + 1_000L, now))
        assertEquals(now - 5L, UpdatePolicy.normalizeLastPrompt(now - 5L, now))
        assertEquals(0L, UpdatePolicy.normalizeLastPrompt(0L, now))
    }

    @Test
    fun clockMovedBack_intervalRestartsFromNow() {
        val last = UpdatePolicy.normalizeLastPrompt(now + 10_000L, now)
        assertFalse(UpdatePolicy.shouldPrompt(40, 40, last, now + interval - 1))
        assertTrue(UpdatePolicy.shouldPrompt(40, 40, last, now + interval))
    }
}
