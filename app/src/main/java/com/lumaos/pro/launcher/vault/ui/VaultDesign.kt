package com.lumaos.pro.launcher.vault.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.lumaos.pro.launcher.R
import kotlin.math.roundToInt

/**
 * Vault's visual language.
 *
 * Vault is part of LumaOS Pro, so it does not invent a look: the radii, spacing
 * steps, type sizes, touch-target minimum and spring feel below are the launcher's
 * own tokens, mirrored here because this file is plain-View code that must behave
 * identically on every device LumaOS Pro supports (the launcher's token object is
 * shared with Compose typography helpers, which Vault's activities do not need).
 *
 * `VaultDesignParityTest` asserts that every mirrored number still equals the
 * value in [com.lumaos.pro.launcher.ui.theme.LumaTokens], so this cannot drift.
 *
 * The palette is a deliberately quiet dark surface with one cool accent - the
 * "private space" reads as calmer and more closed than the wallpaper-driven home
 * screen, while using the same shapes, the same fonts and the same rhythm.
 */
object VaultDesign {

    // ── Radii (mirrored from LumaTokens) ──
    const val radiusSmall = 12f
    const val radiusMedium = 16f
    const val radiusLarge = 22f
    const val radiusXLarge = 28f
    const val radiusFull = 999f

    // ── Spacing (mirrored) ──
    const val spaceXXS = 4f
    const val spaceXS = 8f
    const val spaceSM = 12f
    const val spaceMD = 16f
    const val spaceLG = 20f
    const val spaceXL = 24f
    const val spaceXXL = 32f
    const val screenMarginHorizontal = 24f
    const val minTouchTarget = 48f

    // ── Type (mirrored) ──
    const val typeDisplayHeroSp = 34f
    const val typeTitleSp = 20f
    const val typeHeadingSp = 17f
    const val typeBodySp = 15f
    const val typeCaptionSp = 13f
    const val typeSectionHeaderSp = 13f
    const val typeMicroSp = 11f

    // ── Colours ──
    const val colorBackgroundTop = 0xFF0B0E14.toInt()
    const val colorBackgroundBottom = 0xFF04060A.toInt()
    const val colorSurface = 0xFF12161F.toInt()
    const val colorCard = 0x14FFFFFF
    const val colorCardStrong = 0x1FFFFFFF
    const val colorStroke = 0x24FFFFFF
    const val colorTextPrimary = 0xFFF3F6FB.toInt()
    const val colorTextSecondary = 0xFF9AA4B2.toInt()
    const val colorTextTertiary = 0xFF6C7684.toInt()
    const val colorAccent = 0xFF9AD3FF.toInt()
    const val colorAccentSoft = 0x339AD3FF
    const val colorPositive = 0xFF7EE0B0.toInt()
    const val colorWarning = 0xFFF2C078.toInt()
    const val colorDanger = 0xFFFF8F8F.toInt()
    const val colorScrim = 0xCC04060A.toInt()

    // ── Density helpers ──
    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    fun sp(value: Float): Float = value

    fun typefaceRegular(context: Context): Typeface = resources(context, R.font.inter_regular)
    fun typefaceMedium(context: Context): Typeface = resources(context, R.font.inter_medium)
    fun typefaceSemibold(context: Context): Typeface = resources(context, R.font.inter_semibold)
    fun typefaceLight(context: Context): Typeface = resources(context, R.font.inter_light)

    private fun resources(context: Context, id: Int): Typeface =
        runCatching { context.resources.getFont(id) }.getOrElse { Typeface.SANS_SERIF }

    /** Rounded surface used for every card, pill and tile in the Vault UI. */
    fun surfaceDrawable(
        context: Context,
        fill: Int,
        radiusDp: Float,
        strokeColor: Int = colorStroke,
        strokeWidthDp: Float = 1f
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        val density = context.resources.displayMetrics.density
        cornerRadius = radiusDp * density
        setColor(fill)
        if (strokeColor != Color.TRANSPARENT) {
            setStroke((strokeWidthDp * density).roundToInt().coerceAtLeast(1), strokeColor)
        }
    }
}

/**
 * Small programmatic View toolkit.
 *
 * Vault is built with platform Views, not Compose: the launcher's hot surfaces are
 * Views, the camera preview and media players are Views, and a vault that opens in
 * ~100 ms on a low-end phone is part of the product promise. Everything below is
 * deliberately primitive - a card, a row, a button, a PIN pad - so the seven Vault
 * screens stay small and consistent.
 */
object VaultUi {

    fun screen(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(VaultDesign.colorBackgroundTop)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    fun column(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    fun row(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun spacer(context: Context, heightDp: Float): Space = Space(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, VaultDesign.dp(context, heightDp))
    }

    fun wideSpacer(context: Context, widthDp: Float): Space = Space(context).apply {
        layoutParams = LinearLayout.LayoutParams(VaultDesign.dp(context, widthDp), 1)
    }

    fun card(context: Context, paddingDp: Float = VaultDesign.spaceMD): LinearLayout = column(context).apply {
        background = VaultDesign.surfaceDrawable(context, VaultDesign.colorCard, VaultDesign.radiusLarge)
        setPadding(
            VaultDesign.dp(context, paddingDp),
            VaultDesign.dp(context, paddingDp - 2f),
            VaultDesign.dp(context, paddingDp),
            VaultDesign.dp(context, paddingDp - 2f)
        )
    }

    fun text(
        context: Context,
        value: CharSequence,
        sizeSp: Float = VaultDesign.typeBodySp,
        color: Int = VaultDesign.colorTextPrimary,
        weight: Weight = Weight.REGULAR
    ): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        typeface = when (weight) {
            Weight.LIGHT -> VaultDesign.typefaceLight(context)
            Weight.REGULAR -> VaultDesign.typefaceRegular(context)
            Weight.MEDIUM -> VaultDesign.typefaceMedium(context)
            Weight.SEMIBOLD -> VaultDesign.typefaceSemibold(context)
        }
        includeFontPadding = false
        setLineSpacing(0f, 1.25f)
    }

    enum class Weight { LIGHT, REGULAR, MEDIUM, SEMIBOLD }

    fun title(context: Context, value: CharSequence): TextView =
        text(context, value, VaultDesign.typeTitleSp, VaultDesign.colorTextPrimary, Weight.SEMIBOLD)

    fun hero(context: Context, value: CharSequence): TextView =
        text(context, value, VaultDesign.typeDisplayHeroSp, VaultDesign.colorTextPrimary, Weight.SEMIBOLD)

    fun caption(context: Context, value: CharSequence): TextView =
        text(context, value, VaultDesign.typeCaptionSp, VaultDesign.colorTextSecondary, Weight.REGULAR)

    fun sectionHeader(context: Context, value: CharSequence): TextView =
        text(context, value.toString().uppercase(), VaultDesign.typeSectionHeaderSp, VaultDesign.colorTextTertiary, Weight.SEMIBOLD).apply {
            letterSpacing = 0.12f
        }

    /** A full-width primary action. */
    fun primaryButton(context: Context, label: CharSequence, onClick: () -> Unit): TextView =
        button(context, label, VaultDesign.colorAccent, Color.parseColor("#08131F"), onClick)

    fun secondaryButton(context: Context, label: CharSequence, onClick: () -> Unit): TextView =
        button(context, label, VaultDesign.colorCardStrong, VaultDesign.colorTextPrimary, onClick)

    fun dangerButton(context: Context, label: CharSequence, onClick: () -> Unit): TextView =
        button(context, label, 0x22FF8F8F, VaultDesign.colorDanger, onClick)

    private fun button(context: Context, label: CharSequence, fill: Int, textColor: Int, onClick: () -> Unit): TextView =
        text(context, label, VaultDesign.typeBodySp, textColor, Weight.SEMIBOLD).apply {
            gravity = Gravity.CENTER
            background = VaultDesign.surfaceDrawable(context, fill, VaultDesign.radiusFull, Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
            minHeight = VaultDesign.dp(context, VaultDesign.minTouchTarget)
            setPadding(
                VaultDesign.dp(context, VaultDesign.spaceLG),
                VaultDesign.dp(context, VaultDesign.spaceSM),
                VaultDesign.dp(context, VaultDesign.spaceLG),
                VaultDesign.dp(context, VaultDesign.spaceSM)
            )
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = VaultDesign.dp(context, VaultDesign.spaceXS) }
        }

    /**
     * A tappable tile with an icon, a title and a supporting line.
     *
     * Used for the Vault home destinations and for file rows: 56 dp minimum
     * height, one line of secondary text, and a chevron-free, border-light look so
     * the surface stays calm.
     */
    fun tile(
        context: Context,
        icon: android.graphics.drawable.Drawable?,
        title: CharSequence,
        subtitle: CharSequence?,
        onClick: () -> Unit
    ): LinearLayout = row(context).apply {
        background = VaultDesign.surfaceDrawable(context, VaultDesign.colorCard, VaultDesign.radiusLarge)
        isClickable = true
        isFocusable = true
        setPadding(
            VaultDesign.dp(context, VaultDesign.spaceMD),
            VaultDesign.dp(context, VaultDesign.spaceSM),
            VaultDesign.dp(context, VaultDesign.spaceMD),
            VaultDesign.dp(context, VaultDesign.spaceSM)
        )
        minimumHeight = VaultDesign.dp(context, 64f)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = VaultDesign.dp(context, VaultDesign.spaceXS + 2f) }

        if (icon != null) {
            addView(ImageView(context).apply {
                setImageDrawable(icon)
                val size = VaultDesign.dp(context, 30f)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    rightMargin = VaultDesign.dp(context, VaultDesign.spaceMD)
                }
            })
        }
        addView(column(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(text(context, title, VaultDesign.typeHeadingSp, VaultDesign.colorTextPrimary, Weight.MEDIUM))
            if (!subtitle.isNullOrBlank()) {
                addView(caption(context, subtitle).apply { setPadding(0, VaultDesign.dp(context, 2f), 0, 0) })
            }
        })
        setOnClickListener { onClick() }
    }

    /** Empty state: one line of advice, no fake content, no illustration needed. */
    fun emptyState(context: Context, title: String, body: String): LinearLayout = column(context).apply {
        gravity = Gravity.CENTER
        setPadding(
            VaultDesign.dp(context, VaultDesign.spaceXL),
            VaultDesign.dp(context, VaultDesign.spaceXXL),
            VaultDesign.dp(context, VaultDesign.spaceXL),
            VaultDesign.dp(context, VaultDesign.spaceXL)
        )
        addView(text(context, title, VaultDesign.typeHeadingSp, VaultDesign.colorTextSecondary, Weight.MEDIUM).apply {
            gravity = Gravity.CENTER
        })
        addView(caption(context, body).apply {
            gravity = Gravity.CENTER
            setPadding(0, VaultDesign.dp(context, VaultDesign.spaceXS), 0, 0)
        })
    }

    /** A labelled status line, e.g. "Backup · Protected". */
    fun statusLine(context: Context, label: String, value: String, valueColor: Int): LinearLayout = row(context).apply {
        addView(text(context, label, VaultDesign.typeCaptionSp, VaultDesign.colorTextSecondary))
        addView(Space(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
        addView(text(context, value, VaultDesign.typeCaptionSp, valueColor, Weight.MEDIUM))
    }

    /** Progress row used while importing, exporting, backing up or restoring. */
    fun busyRow(context: Context, message: String, onCancel: (() -> Unit)? = null): LinearLayout = row(context).apply {
        background = VaultDesign.surfaceDrawable(context, VaultDesign.colorAccentSoft, VaultDesign.radiusMedium, Color.TRANSPARENT)
        setPadding(
            VaultDesign.dp(context, VaultDesign.spaceMD),
            VaultDesign.dp(context, VaultDesign.spaceSM),
            VaultDesign.dp(context, VaultDesign.spaceMD),
            VaultDesign.dp(context, VaultDesign.spaceSM)
        )
        addView(android.widget.ProgressBar(context).apply {
            val size = VaultDesign.dp(context, 18f)
            layoutParams = LinearLayout.LayoutParams(size, size)
        })
        addView(text(context, message, VaultDesign.typeCaptionSp, VaultDesign.colorAccent).apply {
            setPadding(VaultDesign.dp(context, VaultDesign.spaceSM), 0, 0, 0)
        })
        if (onCancel != null) {
            addView(Space(context).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
            addView(text(context, "Cancel", VaultDesign.typeCaptionSp, VaultDesign.colorAccent, Weight.SEMIBOLD).apply {
                isClickable = true
                setOnClickListener { onCancel() }
            })
        }
    }

    /** Thin hairline separator. */
    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(0x1AFFFFFF)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            VaultDesign.dp(context, 1f)
        ).apply { topMargin = VaultDesign.dp(context, VaultDesign.spaceXS) }
    }

    /** Voice for errors: plain language, never a stack trace or an error code. */
    fun message(context: Context, text: String, color: Int = VaultDesign.colorDanger): TextView =
        VaultUi.text(context, text, VaultDesign.typeCaptionSp, color).apply {
            setPadding(
                VaultDesign.dp(context, VaultDesign.spaceMD),
                VaultDesign.dp(context, VaultDesign.spaceXS),
                VaultDesign.dp(context, VaultDesign.spaceMD),
                VaultDesign.dp(context, VaultDesign.spaceXS)
            )
        }
}
