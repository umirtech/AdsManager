package com.umirtech.adsmanager

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "AdsConsent"

/**
 * Google UMP consent flow (GDPR/ePrivacy, EEA/UK) for the ads stack.
 *
 * Sequence: [requestConsent] (Activity) → consent form if required →
 * [RequestConfiguration] (content rating + test devices) →
 * [MobileAds.initialize] on a background thread. Ad requests stay blocked
 * until [AdsManager.canRequestAds] flips to true.
 */
internal object ConsentManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var completed = false

    fun requestConsent(activity: Activity, onDone: () -> Unit = {}) {
        if (completed) {
            onDone()
            return
        }
        val paramsBuilder = ConsentRequestParameters.Builder()
        if (BuildConfig.DEBUG) {
            // Debug geography override so the consent flow is testable anywhere.
            // Release builds always use the device's real geography.
            val geography = when (AdsManager.configOrNull?.debugConsentGeography) {
                ConsentDebugGeography.NOT_EEA -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER
                ConsentDebugGeography.DISABLED -> null
                else -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
            }
            if (geography != null) {
                paramsBuilder.setConsentDebugSettings(
                    ConsentDebugSettings.Builder(activity).setDebugGeography(geography).build(),
                )
            }
        }
        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        if (BuildConfig.DEBUG && AdsManager.config.resetConsentInDebug) {
            // Test aid: forget any stored decision so the form is required again.
            consentInfo.reset()
        }
        val appContext = activity.applicationContext
        consentInfo.requestConsentInfoUpdate(
            activity,
            paramsBuilder.build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    finish(appContext, consentInfo, onDone)
                }
            },
            { error ->
                Log.w(TAG, "consent info update failed: ${error.message}")
                finish(appContext, consentInfo, onDone)
            },
        )
    }

    fun showPrivacyOptionsForm(activity: Activity) {
        runCatching {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) {
                val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
                val allowed = consentInfo.canRequestAds()
                Log.d(TAG, "privacy options changed, canRequestAds=$allowed")
                AdsManager.setCanRequestAds(allowed)
                updatePrivacyOptionsRequirement(consentInfo)
            }
        }.onFailure { Log.w(TAG, "privacy options form unavailable", it) }
    }

    private fun updatePrivacyOptionsRequirement(consentInfo: ConsentInformation) {
        val status = runCatching { consentInfo.privacyOptionsRequirementStatus }.getOrNull()
        val required = status == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        Log.d(TAG, "privacy options requirement: $status -> required=$required")
        AdsManager.setPrivacyOptionsRequired(required)
    }

    private fun finish(
        appContext: android.content.Context,
        consentInfo: ConsentInformation,
        onDone: () -> Unit,
    ) {
        completed = true
        scope.launch {
            val config = AdsManager.config
            runCatching {
                val requestConfig = RequestConfiguration.Builder()
                    .setMaxAdContentRating(config.maxAdContentRating)
                    .setAgeRestrictedTreatment(
                        if (config.tagForChildDirectedTreatment || config.tagForUnderAgeOfConsent) {
                            AgeRestrictedTreatment.CHILD
                        } else {
                            AgeRestrictedTreatment.UNSPECIFIED
                        },
                    )
                    .apply {
                        if (BuildConfig.DEBUG && config.testDeviceIds.isNotEmpty()) {
                            setTestDeviceIds(config.testDeviceIds)
                        }
                    }
                    .build()
                MobileAds.setRequestConfiguration(requestConfig)
                MobileAds.initialize(appContext) {}
            }.onFailure { Log.w(TAG, "MobileAds init failed", it) }

            val allowed = config.adsEnabled &&
                runCatching { consentInfo.canRequestAds() }.getOrDefault(false)
            AdsManager.setCanRequestAds(allowed)
            updatePrivacyOptionsRequirement(consentInfo)
            Log.d(TAG, "consent done, canRequestAds=$allowed")
            config.onConsentResolved?.invoke(allowed)
            onDone()
        }
    }
}
