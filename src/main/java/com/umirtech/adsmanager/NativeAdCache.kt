package com.umirtech.adsmanager

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "NativeAdCache"

/**
 * One cached [NativeAd] per slot, shared across screens.
 *
 * - First compose shows the cached ad instantly (no reload flash).
 * - Stale entries (older than the configured TTL) are evicted and reloaded.
 * - [request] with `force = true` reloads in the background while the old ad
 *   keeps showing (no blank gap).
 * - All GMS work runs on [Dispatchers.IO] never the main thread.
 * - Consent gate: nothing is requested until [AdsManager.canRequestAds].
 */
object NativeAdCache {
    private data class Entry(val ad: NativeAd, val loadedAtMs: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private val entries = mutableMapOf<String, Entry>()
    private val flows = mutableMapOf<String, MutableStateFlow<NativeAd?>>()
    private val inFlight = mutableSetOf<String>()

    fun flowFor(slot: String): StateFlow<NativeAd?> = synchronized(lock) {
        flows.getOrPut(slot) { MutableStateFlow(entries[slot]?.takeIfFreshLocked(slot)?.ad) }
            .asStateFlow()
    }

    fun isFresh(slot: String): Boolean = synchronized(lock) {
        entries[slot]?.takeIfFreshLocked(slot) != null
    }

    /**
     * @param force true = reload even if the cached ad is still fresh
     * (periodic refresh). False = only load when empty/stale.
     */
    fun request(
        context: Context,
        slot: String,
        adUnitId: String,
        force: Boolean = false,
        onError: (() -> Unit)? = null,
    ) {
        if (!AdsManager.adsEnabled || !AdsManager.canRequestAds.value) {
            Log.d(TAG, "slot=$slot skipped, no ads/consent yet")
            return
        }
        synchronized(lock) {
            val fresh = entries[slot]?.takeIfFreshLocked(slot) != null
            if (!force && (fresh || slot in inFlight)) return
            if (!fresh) {
                entries.remove(slot)?.ad?.destroy()
                flows[slot]?.value = null
            }
            inFlight += slot
        }
        val appContext = context.applicationContext
        scope.launch {
            try {
                val loader = AdLoader.Builder(appContext, adUnitId)
                    .forNativeAd { ad -> onLoaded(slot, ad) }
                    .withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Log.w(TAG, "slot=$slot code=${error.code} msg=${error.message}")
                            synchronized(lock) { inFlight -= slot }
                            onError?.invoke()
                        }
                    })
                    .withNativeAdOptions(
                        NativeAdOptions.Builder()
                            .setRequestCustomMuteThisAd(true)
                            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_LEFT)
                            .build(),
                    )
                    .build()
                loader.loadAd(AdRequest.Builder().build())
            } catch (t: Throwable) {
                Log.w(TAG, "slot=$slot request failed", t)
                synchronized(lock) { inFlight -= slot }
                onError?.invoke()
            }
        }
    }

    /** AdMob delivers this callback on the main thread. */
    private fun onLoaded(slot: String, ad: NativeAd) {
        Log.d(TAG, "slot=$slot headline=${ad.headline}")
        synchronized(lock) {
            entries[slot]?.ad?.destroy()
            entries[slot] = Entry(ad, SystemClock.elapsedRealtime())
            inFlight -= slot
            flows[slot]?.value = ad
        }
    }

    /** Releases every cached ad; call when ads are disabled at runtime. */
    fun clear() {
        synchronized(lock) {
            entries.values.forEach { it.ad.destroy() }
            entries.clear()
            flows.values.forEach { it.value = null }
        }
    }

    private fun Entry.takeIfFreshLocked(slot: String): Entry? {
        val ttlMs = (AdsManager.configOrNull?.adTtlMinutes ?: 55L) * 60_000L
        val fresh = SystemClock.elapsedRealtime() - loadedAtMs < ttlMs
        if (!fresh) Log.d(TAG, "slot=$slot cached ad expired")
        return if (fresh) this else null
    }
}
