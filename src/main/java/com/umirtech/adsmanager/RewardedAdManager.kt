package com.umirtech.adsmanager

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "RewardedAd"

/**
 * Single-slot rewarded manager: preload with [load], then [show]. The reward
 * callback only fires when the user earns the reward (per AdMob semantics);
 * [show] without a loaded ad reports failure and starts a reload.
 */
object RewardedAdManager {

    private val lock = Any()
    private var ad: RewardedAd? = null
    private var loading = false

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** Preloads a rewarded ad; no-op without consent, config or ads enabled. */
    fun load(context: Context) {
        val unitId = AdsManager.resolvedRewardedUnitId() ?: return
        if (!AdsManager.adsEnabled || !AdsManager.canRequestAds.value) return
        synchronized(lock) {
            if (ad != null || loading) return
            loading = true
        }
        val appContext = context.applicationContext
        RewardedAd.load(
            appContext,
            unitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
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
     * Shows the loaded rewarded ad. [onRewarded] fires only when the user
     * earned the reward; [onDismiss] always fires after the ad closes.
     */
    fun show(
        activity: Activity,
        onRewarded: (RewardItem) -> Unit,
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
            onFailed?.invoke("no rewarded ad loaded")
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
        current.show(activity) { rewardItem -> onRewarded(rewardItem) }
    }

    /** Drops any cached rewarded ad (e.g. consent revoked). */
    fun clear() {
        synchronized(lock) {
            ad = null
            loading = false
            _isReady.value = false
        }
    }
}
