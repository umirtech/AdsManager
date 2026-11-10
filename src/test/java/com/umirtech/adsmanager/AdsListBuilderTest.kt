package com.umirtech.adsmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsListBuilderTest {

    @Test
    fun insertsAdAfterEveryInterval() {
        val entries = buildAdsList(listOf("a", "b", "c", "d", "e"), adInterval = 2, adsEnabled = true)
        assertEquals(7, entries.size)
        assertTrue(entries[0] is ListEntry.ContentItem)
        assertTrue(entries[2] is ListEntry.NativeAdItem)
        assertTrue(entries[5] is ListEntry.NativeAdItem)
        assertEquals(listOf("a", "b", "c", "d", "e"), entries.filterIsInstance<ListEntry.ContentItem<String>>().map { it.data })
    }

    @Test
    fun noAdsWhenDisabledOrIntervalZero() {
        assertEquals(3, buildAdsList(listOf("a", "b", "c"), adInterval = 2, adsEnabled = false).size)
        assertEquals(3, buildAdsList(listOf("a", "b", "c"), adInterval = 0, adsEnabled = true).size)
    }

    @Test
    fun adSlotsAreStablePerPosition() {
        val first = buildAdsList(listOf("a", "b"), adInterval = 2, adsEnabled = true)
        val second = buildAdsList(listOf("a", "b"), adInterval = 2, adsEnabled = true)
        assertEquals(
            (first[2] as ListEntry.NativeAdItem).slotId,
            (second[2] as ListEntry.NativeAdItem).slotId,
        )
    }
}
