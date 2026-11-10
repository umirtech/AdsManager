package com.umirtech.adsmanager

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.RequestConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How debug builds spoof consent geography.
 * - [EEA]: force the GDPR consent flow (default; lets you test the form anywhere).
 * - [NOT_EEA]: force "GDPR not applicable" (simulates India, US, ...).
 * - [DISABLED]: use the device's real geography even in debug.
 */
enum class ConsentDebugGeography { EEA, NOT_EEA, DISABLED }

/**
 * Configuration for [AdsManager]. Unit IDs belong to the host app: supply your
 * production IDs here the library never ships app-specific IDs. Debug builds
 * are served Google test units automatically; in release an unset (null)
 * placement stays disabled instead of falling back to test units. The host app
 * must also declare its AdMob `com.google.android.gms.ads.APPLICATION_ID`
 * meta-data in its own manifest.
 */
data class AdsManagerConfig(
    /** Native ad unit used by in-feed (list) rows; null disables row ads in release. */
    val imageNativeUnitId: String?,
    /** Native ad unit for full-card placements (empty state, end of list); null disables them in release. */
    val videoNativeUnitId: String?,
    /** Interstitial unit; null disables [InterstitialAdManager]. */
    val interstitialUnitId: String? = null,
    /** Rewarded unit; null disables [RewardedAdManager]. */
    val rewardedUnitId: String? = null,
    /** Master switch: when false no ad is requested or rendered. */
    val adsEnabled: Boolean = true,
    /** AdMob test-device hashed IDs (debug aid; test units are used in debug anyway). */
    val testDeviceIds: List<String> = emptyList(),
    /** Content rating ceiling, e.g. [RequestConfiguration.MAX_AD_CONTENT_RATING_PG]. */
    val maxAdContentRating: String = RequestConfiguration.MAX_AD_CONTENT_RATING_PG,
    /** COPPA child-directed tag; true maps to `AgeRestrictedTreatment.CHILD`. */
    val tagForChildDirectedTreatment: Boolean = false,
    /** GDPR under-age-of-consent tag; true maps to `AgeRestrictedTreatment.CHILD`. */
    val tagForUnderAgeOfConsent: Boolean = false,
    /** Insert an in-feed ad after every N content rows (0 disables row ads). */
    val rowAdInterval: Int = 3,
    /** End-of-list card ad only when the folder has at least this many files. */
    val endAdMinFiles: Int = 1,
    /** Cached native ads are reloaded after this many minutes. */
    val adTtlMinutes: Long = 55,
    /** Background refresh cadence for visible slots, in minutes. */
    val adRefreshMinutes: Long = 5,
    /**
     * Called when the UMP consent flow resolves; `true` = user consented.
     * Wire your analytics consent here, e.g. `AnalyticsKit::setConsent`.
     */
    val onConsentResolved: ((analyticsAllowed: Boolean) -> Unit)? = null,
    /**
     * Debug/testing aid (honored in debug builds only): wipes the stored UMP
     * consent decision on every launch so the GDPR form shows again.
     */
    val resetConsentInDebug: Boolean = false,
    /** Debug geography override; release builds always use real geography. */
    val debugConsentGeography: ConsentDebugGeography = ConsentDebugGeography.EEA,
)

/**
 * Entry point for the reusable ads stack: UMP consent, Mobile Ads init and
 * the native/interstitial/rewarded managers.
 *
 * Typical application wiring:
 * ```
 * AdsManager.init(this, AdsManagerConfig(
 *     imageNativeUnitId = "...",
 *     videoNativeUnitId = "...",
 *     interstitialUnitId = "...",
 *     rewardedUnitId = "...",
 *     onConsentResolved = { AnalyticsKit.setConsent(it) },
 * ))
 * // Activity:
 * AdsManager.requestConsent(this)
 * ```
 * Debug builds automatically use Google's test ad units.
 */
object AdsManager {

    const val TEST_NATIVE_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
    const val TEST_INTERSTITIAL_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_REWARDED_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    /** Cache slot ids for the shared placements. */
    const val SLOT_EMPTY = "slot_empty"
    const val SLOT_END = "slot_end"

    @Volatile
    internal var configOrNull: AdsManagerConfig? = null

    @Volatile
    private var appContextOrNull: Context? = null

    private val _canRequestAds = MutableStateFlow(false)

    /** True once UMP consent allows ad requests; gates every ad load. */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)

    /**
     * True when UMP reports the privacy options form must be reachable from
     * the app (GDPR regions, e.g. EEA/UK/Switzerland). Hide your
     * "Ad privacy choices" entry point when false (India, US, etc. unless the
     * region requires it). Stays false until the consent flow resolves.
     */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /** Current configuration; throws when [init] was not called. */
    val config: AdsManagerConfig
        get() = configOrNull ?: error("AdsManager.init(context, config) not called")

    internal val appContext: Context
        get() = appContextOrNull ?: error("AdsManager.init(context, config) not called")

    fun init(context: Context, config: AdsManagerConfig) {
        appContextOrNull = context.applicationContext
        configOrNull = config
    }

    val adsEnabled: Boolean get() = configOrNull?.adsEnabled ?: false

    /** Native unit for list rows (test unit in debug); null when unconfigured in release. */
    fun resolvedImageUnitId(): String? =
        if (BuildConfig.DEBUG) TEST_NATIVE_UNIT_ID else configOrNull?.imageNativeUnitId

    /** Native unit for cards (test unit in debug); null when unconfigured in release. */
    fun resolvedVideoUnitId(): String? =
        if (BuildConfig.DEBUG) TEST_NATIVE_UNIT_ID else configOrNull?.videoNativeUnitId

    /** Interstitial unit (test unit in debug); null when not configured. */
    fun resolvedInterstitialUnitId(): String? =
        if (BuildConfig.DEBUG) TEST_INTERSTITIAL_UNIT_ID else configOrNull?.interstitialUnitId

    /** Rewarded unit (test unit in debug); null when not configured. */
    fun resolvedRewardedUnitId(): String? =
        if (BuildConfig.DEBUG) TEST_REWARDED_UNIT_ID else configOrNull?.rewardedUnitId

    /** Runs the UMP flow, then initializes Mobile Ads. Safe to call once. */
    fun requestConsent(activity: Activity, onDone: () -> Unit = {}) =
        ConsentManager.requestConsent(activity, onDone)

    /**
     * Re-opens the consent/privacy options form when available. Only present
     * this entry point where [privacyOptionsRequired] is true.
     */
    fun showPrivacyOptionsForm(activity: Activity) =
        ConsentManager.showPrivacyOptionsForm(activity)

    internal fun setCanRequestAds(allow: Boolean) {
        _canRequestAds.value = allow
    }

    internal fun setPrivacyOptionsRequired(required: Boolean) {
        _privacyOptionsRequired.value = required
    }
}
