package com.umirtech.adsmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsUnitResolutionTest {

    @Test
    fun unconfiguredNativeUnitsFollowBuildType() {
        AdsManager.configOrNull = null
        assertEquals(BuildConfig.DEBUG, AdsManager.resolvedImageUnitId() != null)
        assertEquals(BuildConfig.DEBUG, AdsManager.resolvedVideoUnitId() != null)
    }

    @Test
    fun unsetImageUnitDropsRowAdSlotsInRelease() {
        AdsManager.configOrNull = AdsManagerConfig(imageNativeUnitId = null, videoNativeUnitId = null)
        try {
            val entries = buildAdsCombinedDisplayList(listOf("a", "b", "c", "d"), adInterval = 2)
            assertEquals(BuildConfig.DEBUG, entries.any { it is ListEntry.NativeAdItem })
        } finally {
            AdsManager.configOrNull = null
        }
    }

    @Test
    fun configuredImageUnitInterleavesRowAdSlots() {
        AdsManager.configOrNull = AdsManagerConfig(
            imageNativeUnitId = "ca-app-pub-0000000000000000/0000000000",
            videoNativeUnitId = "ca-app-pub-0000000000000000/0000000000",
        )
        try {
            val entries = buildAdsCombinedDisplayList(listOf("a", "b", "c", "d"), adInterval = 2)
            assertTrue(entries.any { it is ListEntry.NativeAdItem })
        } finally {
            AdsManager.configOrNull = null
        }
    }
}
