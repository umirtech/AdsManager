package com.umirtech.adsmanager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.nativead.NativeAd
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** In-feed list item: either the app's own content or a native ad slot. */
sealed class ListEntry<out T> {
    data class ContentItem<T>(val data: T) : ListEntry<T>()
    data class NativeAdItem(val slotId: String) : ListEntry<Nothing>()
}

fun ListEntry.NativeAdItem.uniqueId(): String = slotId

/**
 * Interleaves native ad slots into a content list: one ad after every
 * [adInterval] rows (0 disables), or the raw list when ads are disabled or no
 * row unit is configured for this build.
 */
fun <T> buildAdsCombinedDisplayList(
    contentItems: List<T>,
    adInterval: Int = AdsManager.configOrNull?.rowAdInterval ?: 3,
): List<ListEntry<T>> = buildAdsList(
    contentItems,
    adInterval,
    AdsManager.adsEnabled && AdsManager.resolvedImageUnitId() != null,
)

internal fun <T> buildAdsList(
    contentItems: List<T>,
    adInterval: Int,
    adsEnabled: Boolean,
): List<ListEntry<T>> {
    if (!adsEnabled || adInterval <= 0) return contentItems.map { ListEntry.ContentItem(it) }
    val result = mutableListOf<ListEntry<T>>()
    for ((i, item) in contentItems.withIndex()) {
        result.add(ListEntry.ContentItem(item))
        if ((i + 1) % adInterval == 0) {
            val slotId = Random(i).nextInt().toString()
            result.add(ListEntry.NativeAdItem(slotId))
        }
    }
    return result
}

/**
 * Shared cache/refresh shell for every native slot.
 *
 * - Renders nothing until an ad is fully loaded no placeholder, shimmer,
 *   mock, or error is ever shown unless the caller supplies `loadingSkeleton`.
 * - A null [adUnitId] renders nothing (placement unconfigured for this build).
 * - Requests only fire while [NetworkMonitor.isOnline] and consent allows.
 * - Shows the cached ad instantly when fresh; background refresh swaps it in
 *   with no blank gap.
 */
@Composable
internal fun SlotNativeAd(
    slotId: String,
    adUnitId: String?,
    loadingSkeleton: (@Composable () -> Unit)? = null,
    renderAd: @Composable (NativeAd) -> Unit,
) {
    if (!AdsManager.adsEnabled) return
    val unitId = adUnitId ?: return
    val appContext = LocalContext.current.applicationContext
    val cachedAd by NativeAdCache.flowFor(slotId).collectAsStateWithLifecycle()
    val isOnline by NetworkMonitor.isOnline.collectAsState()
    val canRequestAds by AdsManager.canRequestAds.collectAsStateWithLifecycle()

    LaunchedEffect(slotId, unitId, isOnline, canRequestAds) {
        if (!isOnline || !canRequestAds) return@LaunchedEffect
        NativeAdCache.request(appContext, slotId, unitId)
        while (true) {
            val refreshMs = (AdsManager.configOrNull?.adRefreshMinutes ?: 5L) * 60_000L
            delay(refreshMs.milliseconds)
            if (NetworkMonitor.isOnline.value && AdsManager.canRequestAds.value) {
                NativeAdCache.request(appContext, slotId, unitId, force = true)
            }
        }
    }

    if (isOnline) {
        val ad = cachedAd?.takeIf { NativeAdCache.isFresh(slotId) }
        if (ad != null) {
            renderAd(ad)
        } else {
            loadingSkeleton?.let { it() }
        }
    }
}
