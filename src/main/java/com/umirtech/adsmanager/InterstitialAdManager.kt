package com.umirtech.adsmanager

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "InterstitialAd"

/**
 * Single-slot interstitial manager: preload with [load], then [show].
 * Consent-gated and debug-test-unit aware. After a show (or a failed show)
 * the next ad is preloaded automatically.
 */
object InterstitialAdManager {

    private val lock = Any()
    private var ad: InterstitialAd? = null
    private var loading = false

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** Preloads an interstitial; no-op without consent, config or ads enabled. */
    fun load(context: Context) {
        val unitId = AdsManager.resolvedInterstitialUnitId() ?: return
        if (!AdsManager.adsEnabled || !AdsManager.canRequestAds.value) return
        synchronized(lock) {
            if (ad != null || loading) return
            loading = true
        }
        val appContext = context.applicationContext
        InterstitialAd.load(
            appContext,
            unitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    synchronized(lock) {
                        ad = loaded
                        loading = false
                        _isReady.value = true
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "load failed code=${error.code} msg=${error.message}")
                    synchronized(lock) {
                        ad = null
                        loading = false
                        _isReady.value = false
                    }
                }
            },
        )
    }

    /**
     * Shows the loaded interstitial. When nothing is ready, [onFailed] fires
     * and a reload starts; consent-loss also reports failure.
     */
    fun show(
        activity: Activity,
        onDismiss: (() -> Unit)? = null,
        onFailed: ((String) -> Unit)? = null,
    ) {
        if (!AdsManager.adsEnabled || !AdsManager.canRequestAds.value) {
            onFailed?.invoke("ads unavailable (disabled or no consent)")
            return
        }
        val current = synchronized(lock) {
            val cached = ad
            ad = null
            _isReady.value = false
            cached
        }
        if (current == null) {
            onFailed?.invoke("no interstitial loaded")
            load(activity)
            return
        }
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                load(activity)
                onDismiss?.invoke()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.w(TAG, "show failed code=${error.code} msg=${error.message}")
                load(activity)
                onFailed?.invoke(error.message)
            }
        }
        current.show(activity)
    }

    /** Drops any cached interstitial (e.g. consent revoked). */
    fun clear() {
        synchronized(lock) {
            ad = null
            loading = false
            _isReady.value = false
        }
    }
}
