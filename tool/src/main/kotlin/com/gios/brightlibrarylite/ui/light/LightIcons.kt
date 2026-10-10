package com.gios.brightlibrarylite.ui.light

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.thelightphone.sdk.ui.LightIcons as Sdk

/**
 * LightOS's own icon set, by the names the full app used. The drawables are the SDK's
 * (`com.thelightphone.sdk.ui.LightIcons`); this only keeps the call sites of the port unchanged
 * and adds the [tint] the SDK's own `LightIcon` does not take.
 */
class LightIconSpec(val name: String, val res: Int)

object LightIcons {
    val Back = LightIconSpec("back", Sdk.BACK.drawableResource)
    val Forward = LightIconSpec("forward", Sdk.ARROW_RIGHT.drawableResource)
    val Add = LightIconSpec("add book", Sdk.ADD.drawableResource)
    val Settings = LightIconSpec("settings", Sdk.SETTINGS.drawableResource)
    val Search = LightIconSpec("search", Sdk.SEARCH.drawableResource)
    val Close = LightIconSpec("close", Sdk.CLOSE.drawableResource)
    val Accept = LightIconSpec("confirm", Sdk.ACCEPT.drawableResource)
    val Trash = LightIconSpec("delete", Sdk.TRASH.drawableResource)
    val Backspace = LightIconSpec("backspace", Sdk.DELETE.drawableResource)
    val Pencil = LightIconSpec("edit", Sdk.PENCIL.drawableResource)
    val Star = LightIconSpec("bookmarked", Sdk.STAR.drawableResource)
    val StarOutline = LightIconSpec("bookmark", Sdk.STAR_OUTLINE.drawableResource)
    val List = LightIconSpec("contents", Sdk.LIST.drawableResource)
    val SelectOn = LightIconSpec("on", Sdk.SELECT_ON.drawableResource)
    val SelectOff = LightIconSpec("off", Sdk.SELECT_OFF.drawableResource)
    val Refresh = LightIconSpec("try again", Sdk.REFRESH.drawableResource)
    val Up = LightIconSpec("up", Sdk.UP.drawableResource)
    val Down = LightIconSpec("down", Sdk.DOWN.drawableResource)
    val Spacer = LightIconSpec("", Sdk.SPACER.drawableResource)
}

private const val DEFAULT_SIZE_UNITS = 2f

/**
 * Icons are sized in grid units and take the content colour, as in LightOS. [tint] exists
 * only for the cases the SDK doesn't have: a bar item that has to recede.
 */
@Composable
fun LightIcon(
    icon: LightIconSpec,
    modifier: Modifier = Modifier,
    size: Float = DEFAULT_SIZE_UNITS,
    tint: Color? = null,
    contentDescription: String? = icon.name,
) {
    Icon(
        painter = painterResource(icon.res),
        contentDescription = contentDescription?.takeIf { it.isNotBlank() },
        tint = tint ?: LightThemeTokens.colors.content,
        modifier = modifier.size(size.gridUnitsAsDp()),
    )
}
