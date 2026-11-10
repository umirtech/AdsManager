package com.umirtech.adsmanager

import android.content.Context
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import kotlin.math.max

/**
 * Colors applied to the native (non-Compose) ad views. Derived from
 * [MaterialTheme.colorScheme] so every placement follows the app's light or
 * dark theme; re-applied through the AndroidView `update` callback when the
 * device theme changes.
 */
internal data class AdPalette(
    val container: Int,
    val headline: Int,
    val body: Int,
    val iconCircle: Int,
    val iconTint: Int,
    val mediaPlaceholder: Int,
    val ctaContainer: Int,
    val ctaText: Int,
)

@Composable
private fun rememberAdPalette(): AdPalette {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) {
        AdPalette(
            container = scheme.surfaceContainerHigh.toArgb(),
            headline = scheme.onSurface.toArgb(),
            body = scheme.onSurfaceVariant.toArgb(),
            iconCircle = scheme.secondaryContainer.toArgb(),
            iconTint = scheme.onSecondaryContainer.toArgb(),
            mediaPlaceholder = scheme.surfaceContainerHighest.toArgb(),
            ctaContainer = scheme.secondaryContainer.toArgb(),
            ctaText = scheme.onSecondaryContainer.toArgb(),
        )
    }
}

private fun dp(v: Int, context: Context): Int =
    (v * context.resources.displayMetrics.density).toInt()

private fun pillBackground(color: Int, radiusPx: Float): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    setColor(color)
    cornerRadius = radiusPx
}

private fun circleBackground(color: Int): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(color)
}

private fun cardBackground(color: Int): GradientDrawable = GradientDrawable().apply {
    shape = GradientDrawable.RECTANGLE
    setColor(color)
}

private fun buildNativeAdView(
    context: Context,
    ad: NativeAd,
    palette: AdPalette,
    paddingLeft: Int = 0,
    paddingTop: Int = 0,
    paddingRight: Int = 0,
    paddingBottom: Int = 0,
): NativeAdView {
    val pad = dp(16, context)
    val adView = NativeAdView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        )
        setPadding(
            dp(paddingLeft, context),
            dp(paddingTop, context),
            dp(paddingRight, context),
            dp(paddingBottom, context),
        )
    }
    adView.background = cardBackground(palette.container)

    val rootView = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        setPadding(pad, pad, pad, pad)
    }
    adView.addView(rootView)

    val topRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
    }
    rootView.addView(topRow)

    val iconSize = dp(60, context)
    val iconBg = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        background = circleBackground(palette.iconCircle)
    }

    val iconView = ImageView(context).apply {
        layoutParams = FrameLayout.LayoutParams(dp(24, context), dp(24, context), Gravity.CENTER)
        setImageResource(android.R.drawable.ic_dialog_info)
        setColorFilter(palette.iconTint)
    }

    val artwork: Drawable? = runCatching {
        ad.icon?.drawable ?: ad.images.firstOrNull()?.drawable
    }.getOrNull()

    artwork?.let {
        iconView.layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        iconView.clearColorFilter()
        iconView.scaleType = ImageView.ScaleType.CENTER_CROP
        iconView.clipToOutline = true
        iconView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        iconView.setImageDrawable(artwork)
    }

    iconBg.addView(iconView)
    topRow.addView(iconBg)

    val textCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply { setMargins(dp(16, context), 0, dp(8, context), 0) }
    }
    topRow.addView(textCol)

    val headlineView = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        text = ad.headline ?: context.getString(R.string.adsmanager_fallback_headline)
        setTextColor(palette.headline)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    textCol.addView(headlineView)

    val bodyText = ad.body ?: ad.advertiser ?: ""
    val bodyView = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        text = bodyText
        setTextColor(palette.body)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        visibility = if (bodyText.isBlank()) View.GONE else View.VISIBLE
    }
    textCol.addView(bodyView)

    var mediaView: MediaView? = null
    ad.mediaContent?.let { mediaContent ->
        mediaView = MediaView(context).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(max((140 * mediaContent.aspectRatio).toInt(), 140), context),
            )
            lp.setMargins(0, dp(12, context), 0, 0)
            layoutParams = lp
        }
        mediaView.clipToOutline = true
        mediaView.background = pillBackground(palette.mediaPlaceholder, dp(10, context).toFloat())
        rootView.addView(mediaView)
    }

    val ctaView = TextView(context).apply {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        lp.setMargins(0, dp(12, context), 0, 0)
        lp.gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = lp
        text = ad.callToAction?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.adsmanager_fallback_cta)
        gravity = Gravity.CENTER
        setTextColor(palette.ctaText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        background = pillBackground(palette.ctaContainer, dp(100, context).toFloat())
        setPadding(dp(18, context), dp(5, context), dp(18, context), dp(5, context))
    }
    rootView.addView(ctaView)

    adView.headlineView = headlineView
    adView.bodyView = bodyView
    adView.callToActionView = ctaView
    adView.iconView = iconView
    if (mediaView != null) adView.mediaView = mediaView

    adView.setNativeAd(ad)
    return adView
}

private fun buildRowAdView(
    context: Context,
    ad: NativeAd,
    palette: AdPalette,
    paddingLeft: Int = 0,
    paddingTop: Int = 0,
    paddingRight: Int = 0,
    paddingBottom: Int = 0,
): NativeAdView {
    val pad = dp(16, context)
    val adView = NativeAdView(context).apply {
        layoutParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT,
        )
        setPadding(
            dp(paddingLeft, context),
            dp(paddingTop, context),
            dp(paddingRight, context),
            dp(paddingBottom, context),
        )
    }
    adView.background = cardBackground(palette.container)

    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        )
        setPadding(pad, pad, pad, pad)
    }

    val iconSize = dp(48, context)
    val iconBg = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        background = circleBackground(palette.iconCircle)
    }

    val iconView = ImageView(context).apply {
        layoutParams = FrameLayout.LayoutParams(dp(24, context), dp(24, context), Gravity.CENTER)
        setImageResource(android.R.drawable.ic_dialog_info)
        setColorFilter(palette.iconTint)
    }

    val artwork: Drawable? = runCatching {
        ad.icon?.drawable ?: ad.images.firstOrNull()?.drawable
    }.getOrNull()

    if (artwork != null) {
        iconView.layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
        iconView.clearColorFilter()
        iconView.scaleType = ImageView.ScaleType.CENTER_CROP
        iconView.clipToOutline = true
        iconView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        iconView.setImageDrawable(artwork)
    }
    iconBg.addView(iconView)
    row.addView(iconBg)

    val textCol = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        p.setMargins(dp(16, context), 0, dp(12, context), 0)
        layoutParams = p
    }
    row.addView(textCol)

    val headlineView = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        text = ad.headline ?: context.getString(R.string.adsmanager_fallback_headline)
        setTextColor(palette.headline)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    textCol.addView(headlineView)

    val bodyText = ad.body ?: ad.advertiser ?: ""
    val bodyView = TextView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        text = bodyText
        setTextColor(palette.body)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        visibility = if (bodyText.isBlank()) View.GONE else View.VISIBLE
    }
    textCol.addView(bodyView)

    val ctaView = TextView(context).apply {
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        lp.gravity = Gravity.CENTER_VERTICAL
        layoutParams = lp
        text = ad.callToAction?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.adsmanager_fallback_cta)
        gravity = Gravity.CENTER
        setTextColor(palette.ctaText)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        background = pillBackground(palette.ctaContainer, dp(100, context).toFloat())
        setPadding(dp(14, context), dp(5, context), dp(14, context), dp(5, context))
    }
    row.addView(ctaView)

    adView.addView(row)

    adView.headlineView = headlineView
    adView.bodyView = bodyView
    adView.callToActionView = ctaView
    adView.iconView = iconView

    adView.setNativeAd(ad)
    return adView
}

private fun buildAdBadge(context: Context, palette: AdPalette): TextView = TextView(context).apply {
    gravity = Gravity.CENTER
    text = context.getString(R.string.adsmanager_badge)
    setTextColor(palette.ctaText)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    background = pillBackground(palette.ctaContainer, dp(6, context).toFloat())
    setPadding(dp(8, context), dp(3, context), dp(8, context), dp(3, context))
}

private class AdViewState {
    var ad: NativeAd? = null
    var palette: AdPalette? = null
}

/**
 * List/row native ad (in-feed). Theme-aware via MaterialTheme; the AndroidView
 * update callback re-applies colors when the device theme flips.
 */
@Composable
fun NativeRowAd(
    slotId: String,
    modifier: Modifier = Modifier,
    adUnitId: String? = AdsManager.resolvedImageUnitId(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val direction = LocalLayoutDirection.current
    val leftPadding = contentPadding.calculateLeftPadding(direction)
    val rightPadding = contentPadding.calculateRightPadding(direction)
    val topPadding = contentPadding.calculateTopPadding()
    val bottomPadding = contentPadding.calculateBottomPadding()
    val palette = rememberAdPalette()

    SlotNativeAd(
        slotId = slotId,
        adUnitId = adUnitId,
        loadingSkeleton = {
            val transition = rememberInfiniteTransition(label = "skeleton")
            val alpha by transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "skeleton_alpha",
            )
            val skeletonColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
            Box(modifier) {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(contentPadding + PaddingValues(10.dp)),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(skeletonColor),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(13.dp)
                                .clip(RoundedCornerShape(4.dp)).background(skeletonColor),
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().height(12.dp)
                                .clip(RoundedCornerShape(4.dp)).background(skeletonColor),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier.height(12.dp).width(40.dp)
                            .clip(RoundedCornerShape(8.dp)).background(skeletonColor),
                    )
                }
            }
        },
    ) { ad ->
        val state = remember { AdViewState() }
        AndroidView(
            modifier = modifier.fillMaxWidth(),
            factory = { ctx ->
                RelativeLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    )
                }
            },
            update = { root ->
                if (state.ad !== ad || state.palette != palette) {
                    state.ad = ad
                    state.palette = palette
                    root.removeAllViews()
                    root.addView(
                        buildRowAdView(
                            root.context, ad, palette,
                            paddingLeft = leftPadding.value.toInt(),
                            paddingRight = rightPadding.value.toInt(),
                            paddingTop = topPadding.value.toInt(),
                            paddingBottom = bottomPadding.value.toInt(),
                        ),
                    )
                    root.addView(
                        buildAdBadge(root.context, palette).apply {
                            layoutParams = RelativeLayout.LayoutParams(
                                RelativeLayout.LayoutParams.WRAP_CONTENT,
                                RelativeLayout.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE)
                                addRule(RelativeLayout.ALIGN_PARENT_END, RelativeLayout.TRUE)
                                setMargins(
                                    0,
                                    dp(topPadding.value.toInt(), root.context),
                                    dp(rightPadding.value.toInt(), root.context),
                                    0,
                                )
                            }
                        },
                    )
                }
            },
        )
    }
}

/**
 * Full-card native ad: media banner + centered CTA. Theme-aware via
 * MaterialTheme; the AndroidView update callback re-applies colors when the
 * device theme flips.
 */
@Composable
fun NativeCardAd(
    slotId: String,
    modifier: Modifier = Modifier,
    adUnitId: String? = AdsManager.resolvedVideoUnitId(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val direction = LocalLayoutDirection.current
    val leftPadding = contentPadding.calculateLeftPadding(direction)
    val rightPadding = contentPadding.calculateRightPadding(direction)
    val topPadding = contentPadding.calculateTopPadding()
    val bottomPadding = contentPadding.calculateBottomPadding()
    val palette = rememberAdPalette()

    SlotNativeAd(
        slotId = slotId,
        adUnitId = adUnitId,
        loadingSkeleton = {
            val transition = rememberInfiniteTransition(label = "skeleton")
            val alpha by transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0.8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "skeleton_alpha",
            )
            val skeletonColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
            Box(modifier) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(contentPadding + PaddingValues(10.dp)),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(skeletonColor),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(13.dp)
                                    .clip(RoundedCornerShape(4.dp)).background(skeletonColor),
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier.fillMaxWidth().height(12.dp)
                                    .clip(RoundedCornerShape(4.dp)).background(skeletonColor),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                            .clip(RoundedCornerShape(8.dp)).background(skeletonColor),
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                            .clip(RoundedCornerShape(8.dp)).background(skeletonColor),
                    )
                }
            }
        },
    ) { ad ->
        val state = remember { AdViewState() }
        AndroidView(
            modifier = modifier.fillMaxWidth(),
            factory = { ctx ->
                RelativeLayout(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    )
                }
            },
            update = { root ->
                if (state.ad !== ad || state.palette != palette) {
                    state.ad = ad
                    state.palette = palette
                    root.removeAllViews()
                    root.addView(
                        buildNativeAdView(
                            root.context, ad, palette,
                            paddingLeft = leftPadding.value.toInt(),
                            paddingRight = rightPadding.value.toInt(),
                            paddingTop = topPadding.value.toInt(),
                            paddingBottom = bottomPadding.value.toInt(),
                        ),
                    )
                    root.addView(
                        buildAdBadge(root.context, palette).apply {
                            layoutParams = RelativeLayout.LayoutParams(
                                RelativeLayout.LayoutParams.WRAP_CONTENT,
                                RelativeLayout.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE)
                                addRule(RelativeLayout.ALIGN_PARENT_END, RelativeLayout.TRUE)
                                setMargins(
                                    0,
                                    dp(topPadding.value.toInt(), root.context),
                                    dp(rightPadding.value.toInt(), root.context),
                                    0,
                                )
                            }
                        },
                    )
                }
            },
        )
    }
}
