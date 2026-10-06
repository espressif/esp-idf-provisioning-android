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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPolicyTest {

    private val now = 1_800_000_000_000L
    private val interval = ReviewPolicy.MIN_INTERVAL_MS

    @Test
    fun belowThreshold_notEligible() {
        assertFalse(ReviewPolicy.isEligible(0, 0L, now))
        assertFalse(ReviewPolicy.isEligible(1, 0L, now))
    }

    @Test
    fun atThreshold_neverPrompted_eligible() {
        assertTrue(ReviewPolicy.isEligible(2, 0L, now))
    }

    @Test
    fun cooldownBoundary() {
        assertFalse(ReviewPolicy.isEligible(2, now - interval + 1, now))
        assertTrue(ReviewPolicy.isEligible(2, now - interval, now))
    }

    @Test
    fun counterResetAfterPrompt_notEligibleUntilTwoNewSuccesses() {
        val last = now - interval
        assertFalse(ReviewPolicy.isEligible(0, last, now))
        assertFalse(ReviewPolicy.isEligible(1, last, now))
        assertTrue(ReviewPolicy.isEligible(2, last, now))
    }

    @Test
    fun lastPromptInFuture_notEligible() {
        assertFalse(ReviewPolicy.isEligible(5, now + 1_000L, now))
    }

    @Test
    fun normalize_clampsOnlyFutureValues() {
        assertEquals(now, ReviewPolicy.normalizeLastPrompt(now + 1_000L, now))
        assertEquals(now - 5L, ReviewPolicy.normalizeLastPrompt(now - 5L, now))
        assertEquals(0L, ReviewPolicy.normalizeLastPrompt(0L, now))
    }

    @Test
    fun clockMovedBack_cooldownRestartsFromNow() {
        val last = ReviewPolicy.normalizeLastPrompt(now + 10_000L, now)
        assertFalse(ReviewPolicy.isEligible(5, last, now + interval - 1))
        assertTrue(ReviewPolicy.isEligible(5, last, now + interval))
    }
}
