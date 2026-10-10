package com.gios.brightlibrarylite.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.ui.LightTheme as SdkLightTheme
import com.thelightphone.sdk.ui.LightThemeColors as SdkColors
import com.gios.brightlibrarylite.ui.light.LightThemeColors
import com.gios.brightlibrarylite.ui.light.LocalLightColors
import com.gios.brightlibrarylite.ui.light.LocalLightTypography
import com.gios.brightlibrarylite.ui.light.rememberLightTypography

/**
 * Pure-black greyscale scheme for the Light Phone III.
 *
 * Every `surface*` role is #000000 rather than Material's dark greys, and
 * `surfaceTint` is black so `surfaceColorAtElevation` - which composites
 * `surfaceTint` at an elevation-derived alpha over `surface` - resolves to
 * black at every elevation. That matters because FastRead's sheets, top bars
 * and book rows all pass `tonalElevation`, and with the stock dark scheme each
 * of those lights up a grey rectangle on an otherwise unlit panel.
 *
 * `error` is white-on-black instead of red: the panel is greyscale, so a red
 * error state would arrive as an unremarkable mid grey with no signal value.
 */
private val LightPhoneColorScheme = darkColorScheme(
    primary = LpWhite,
    onPrimary = LpBlack,
    primaryContainer = LpGrey18,
    onPrimaryContainer = LpWhite,
    inversePrimary = LpBlack,

    secondary = LpGrey85,
    onSecondary = LpBlack,
    secondaryContainer = LpGrey18,
    onSecondaryContainer = LpWhite,

    tertiary = LpGrey65,
    onTertiary = LpBlack,
    tertiaryContainer = LpGrey18,
    onTertiaryContainer = LpWhite,

    background = LpBlack,
    onBackground = LpWhite,

    surface = LpBlack,
    onSurface = LpWhite,
    surfaceTint = LpBlack,
    surfaceBright = LpBlack,
    surfaceDim = LpBlack,

    // Full-bleed backgrounds and our own bottom sheet stay black...
    surfaceContainerLowest = LpBlack,
    surfaceContainerLow = LpBlack,

    // ...but `surfaceContainer`/`High`/`Highest` back Material's *ephemeral*
    // containers: AlertDialog, DropdownMenu, the scrolled TopAppBar. Those
    // float above a scrim, and a scrim over an already-black background tints
    // nothing, so a black dialog on a black screen is just unanchored text with
    // buttons. They get the faintest grey that still reads as a panel. This is
    // the one place the scheme deliberately lights pixels, and it is bounded to
    // a small transient surface rather than the whole screen.
    surfaceContainer = LpGrey18,
    surfaceContainerHigh = LpGrey18,
    surfaceContainerHighest = LpGrey18,

    // `surfaceVariant` is Material's default *track* colour - progress bars,
    // slider inactive track, unchecked switch track. Black would erase all of
    // them: the reader's progress bar would show a white sliver with no sense
    // of the whole, and every slider in Settings would lose its rail.
    surfaceVariant = LpGrey18,
    onSurfaceVariant = LpGrey65,

    inverseSurface = LpWhite,
    inverseOnSurface = LpBlack,

    error = LpWhite,
    onError = LpBlack,
    errorContainer = LpGrey18,
    onErrorContainer = LpWhite,

    outline = LpGrey30,
    outlineVariant = LpGrey18,
    scrim = LpBlack,
)

/**
 * True when the Light Phone scheme is active. Read this wherever a component
 * needs to depart from its normal styling for the greyscale OLED - accent
 * colours that must be flattened to grey, and alpha values that need a floor
 * because matte glass eats low-contrast text.
 */
val LocalIsLightPhone = staticCompositionLocalOf { true }

/**
 * Alpha floors for the Light Phone panel.
 *
 * FastRead leans on low alphas for de-emphasis (context lines at 0.5, the
 * paragraph pilcrow at 0.45, settings captions at 0.6). Behind matte glass
 * those land close to invisible, so in Light Phone mode we raise the floor
 * without discarding the user's relative choices.
 */
object LpContrast {
    /** Cheapest possible fix for a hardcoded de-emphasis alpha. */
    fun floor(alpha: Float, min: Float = 0.72f): Float = alpha.coerceAtLeast(min)

    /** Compresses [alpha] into [min]..1.0, preserving ordering. */
    fun lift(alpha: Float, min: Float = 0.55f): Float =
        (min + (1f - min) * alpha.coerceIn(0f, 1f))
}

/**
 * The tool's one theme: the full app's "Light Phone" mode, pure black and greyscale.
 *
 * The full app also offered upstream FastRead's System, Light and Dark themes. They are gone here:
 * a Light tool is drawn on the LightOS panel, and the reading screens were tuned for black.
 */
@Composable
fun FastReadTheme(content: @Composable () -> Unit) {
    // The SDK's own theme on the outside, so the SDK components used here (the keyboard editor,
    // the text field) get LightOS's font and colours; this app's tokens on the inside.
    SdkLightTheme(colors = SdkColors.Dark) {
        CompositionLocalProvider(
            LocalIsLightPhone provides true,
            LocalLightColors provides LightThemeColors.Dark,
            LocalLightTypography provides rememberLightTypography(),
        ) {
            MaterialTheme(
                colorScheme = LightPhoneColorScheme,
                typography = Typography,
            ) {
                Box(Modifier.fillMaxSize().background(LpBlack)) { content() }
            }
        }
    }
}
