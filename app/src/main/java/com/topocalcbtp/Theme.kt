@file:OptIn(ExperimentalMaterial3Api::class)

package com.topocalcbtp

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** IK TopoCalc brand colours (from the logo). */
object Brand {
    val Navy = Color(0xFF0D2A78)
    val Ink = Color(0xFF071A5C)
    val Yellow = Color(0xFFFDB515)
}

/** Colours not covered by the Material scheme. */
data class AppColors(
    val dark: Boolean,
    val topBar: Color,
    val heading: Color,
    val muted: Color,
    val ok: Color,
    val fieldBackground: Color,
    val fieldText: Color,
    val fieldBorder: Color,
    val fieldFocus: Color,
    /** Bottom bar background (light: light grey, dark: dark grey). */
    val navBar: Color,
    /** Bubble of the active bottom tab (light: black, dark: white). Also the active label colour. */
    val navBox: Color,
    /** Icon drawn inside the bubble (light: white, dark: navy). */
    val navContent: Color
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(false, Color.White, Brand.Navy, Color(0xFF52606D), Color(0xFF1E7B4F), Color.White, Brand.Ink,
        Color(0xFFD9E2EC), Brand.Navy, Color(0xFFE2E5EA), Color(0xFF111111), Color.White)
}

private val LightScheme = lightColorScheme(
    primary = Brand.Navy, onPrimary = Color.White,
    secondary = Brand.Yellow, onSecondary = Brand.Ink,
    secondaryContainer = Brand.Navy, onSecondaryContainer = Color.White, // selected chips
    tertiary = Brand.Navy,
    background = Color(0xFFF4F6FA), onBackground = Brand.Ink,
    surface = Color(0xFFF4F6FA), onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFF0F4F8), onSurfaceVariant = Color(0xFF52606D),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Color.White,
    outline = Color(0xFFD9E2EC), outlineVariant = Color(0xFFD9E2EC),
    error = Color(0xFFC0392B), onError = Color.White
)

/** Dark mode is "inverted": buttons, selected chips, fields and the active tab are white with navy text. */
private val DarkScheme = darkColorScheme(
    primary = Color.White, onPrimary = Brand.Navy,
    secondary = Brand.Yellow, onSecondary = Brand.Ink,
    secondaryContainer = Color.White, onSecondaryContainer = Brand.Navy,
    tertiary = Color(0xFF9DB2FF),
    background = Color(0xFF0D1322), onBackground = Color(0xFFE7ECF5),
    surface = Color(0xFF0D1322), onSurface = Color(0xFFE7ECF5),
    surfaceVariant = Color(0xFF1E2840), onSurfaceVariant = Color(0xFF9AA6BF),
    surfaceContainerLowest = Color(0xFF161E32), surfaceContainerLow = Color(0xFF161E32), surfaceContainer = Color(0xFF161E32),
    surfaceContainerHigh = Color(0xFF1B2440), surfaceContainerHighest = Color(0xFF161E32),
    outline = Color(0xFF2B3754), outlineVariant = Color(0xFF2B3754),
    error = Color(0xFFFF7A7A), onError = Color(0xFF0D1322)
)

private val LightApp = AppColors(false, Color.White, Brand.Navy, Color(0xFF52606D), Color(0xFF1E7B4F), Color.White, Brand.Ink,
    Color(0xFFD9E2EC), Brand.Navy, Color(0xFFE2E5EA), Color(0xFF111111), Color.White)
private val DarkApp = AppColors(true, Color(0xFF0B1120), Color(0xFF9DB2FF), Color(0xFF9AA6BF), Color(0xFF4FD18B), Color.White, Brand.Ink,
    Color.White, Brand.Yellow, Color(0xFF2B2F38), Color.White, Brand.Navy)

@Composable
fun IkTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkApp else LightApp) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
    }
}

// ---------- icons (same drawings as the HTML version) ----------

/** "M x,y a r,r 0 1,0 2r,0 a r,r 0 1,0 -2r,0" = full circle as path data. */
private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

private fun strokeIcon(name: String, width: Float, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach {
            addPath(
                pathData = addPathNodes(it), fill = null, stroke = SolidColor(Color.Black),
                strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
        }
    }.build()

private const val TRIBRACH = "M9.5 12.5l-1 2.5h7l-1-2.5"
private const val TRIPOD = "M9.5 15l-3.5 7M14.5 15l3.5 7M12 15v5.5"

/** Line icon with optional small filled parts (same 24×24 grid and stroke as [strokeIcon]). */
private fun icon(name: String, width: Float, strokes: List<String>, fills: List<String> = emptyList()): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        strokes.forEach {
            addPath(
                pathData = addPathNodes(it), fill = null, stroke = SolidColor(Color.Black),
                strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round
            )
        }
        fills.forEach { addPath(pathData = addPathNodes(it), fill = SolidColor(Color.Black)) }
    }.build()

object IkIcons {
    /**
     * Automatic level: horizontal telescope (objective block at the front, eyepiece at the back) with a
     * small sight/vial on top, a single centre column on a base plate, and a tripod.
     */
    /** Automatic level: telescope body with sight on top, objective hood at the front, eyepiece at the back, on a tripod. */
    val Level = icon("level", 1.8f,
        strokes = listOf(
            "M5.5 8h10.5v4.5h-10.5a1.5 1.5 0 0 1-1.5-1.5v-1.5a1.5 1.5 0 0 1 1.5-1.5z", // telescope body
            "M16 6.5h3a1 1 0 0 1 1 1v5a1 1 0 0 1-1 1h-3z", // objective hood (front)
            "M4 9.25h-1.5v2h1.5", // eyepiece (back)
            "M8.5 8V6.25h3.5V8", // sight on top
            TRIBRACH, TRIPOD
        ))
    /** Theodolite: U-shaped alidade (two standards), telescope seen end-on with its objective lens, on a tripod. */
    val Theodolite = icon("theodolite", 1.8f,
        strokes = listOf(
            "M5.5 5.5v7h13v-7", // standards
            circle(12f, 7f, 3.75f), // telescope
            "M5.5 7h2.5M16 7h2.5", // trunnion axis
            TRIBRACH, TRIPOD
        ),
        fills = listOf(circle(12f, 7f, 1.25f)) // objective lens
    )
    val Calc = strokeIcon("calc", 1.8f, "M4 18l5-9 6 5 5-9",
        circle(4f, 18f, 1.6f), circle(9f, 9f, 1.6f), circle(15f, 14f, 1.6f), circle(20f, 5f, 1.6f))
    val Coord = strokeIcon("coord", 1.8f, circle(12f, 12f, 9f), "M3 12h18M12 3c3 3.5 3 14.5 0 18M12 3c-3 3.5-3 14.5 0 18")
    val Project = strokeIcon("project", 1.8f, "M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val Sun = strokeIcon("sun", 2f, circle(12f, 12f, 4.2f),
        "M12 2.5v2.2M12 19.3v2.2M2.5 12h2.2M19.3 12h2.2M5.3 5.3l1.6 1.6M17.1 17.1l1.6 1.6M5.3 18.7l1.6-1.6M17.1 6.9l1.6-1.6")
    val Moon = strokeIcon("moon", 2f, "M20.5 14.2A8.5 8.5 0 0 1 9.8 3.5a8.5 8.5 0 1 0 10.7 10.7z")
    val Tabs = listOf(Level, Theodolite, Calc, Coord, Project)
}

// ---------- bottom navigation ----------

private val BubbleRadius = 24.dp
/** Bubble centre, measured from the top of the tab row (the active icon is lifted to this height). */
private val BubbleCentreY = 8.dp

/**
 * Equal, fixed-width tabs (opening a tab never moves the others). One circular bubble is drawn behind the
 * row and slides from the old tab to the new one (a spring on a single float, drawn at its position, so
 * nothing is re-measured). The active icon zooms and lifts into the bubble. RTL: tab 0 is on the right.
 */
@Composable
fun IkBottomNav(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalAppColors.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val barColor = c.navBar
    val count = labels.size
    val bubblePos by animateFloatAsState(
        targetValue = selected.toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f), // slight overshoot: feels like a bubble
        label = "bubblePos"
    )
    Column(Modifier.fillMaxWidth().background(barColor)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp).height(62.dp)
                .drawBehind {
                    // centre of tab index p (fractional while the bubble is sliding)
                    val frac = (bubblePos + 0.5f) / count
                    val x = if (rtl) size.width * (1f - frac) else size.width * frac
                    drawCircle(color = c.navBox, radius = BubbleRadius.toPx(), center = Offset(x, BubbleCentreY.toPx()))
                },
            verticalAlignment = Alignment.Bottom
        ) {
            labels.forEachIndexed { i, label ->
                val progress by animateFloatAsState(
                    if (i == selected) 1f else 0f, animationSpec = tween(320, easing = FastOutSlowInEasing), label = "tab$i"
                )
                val iconTint = lerp(c.muted, c.navContent, progress)
                val labelTint = lerp(c.muted, c.navBox, progress)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(
                        Modifier.padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            IkIcons.Tabs[i], contentDescription = label, tint = iconTint,
                            modifier = Modifier.size(24.dp).graphicsLayer {
                                val s = 1f + 0.4f * progress
                                scaleX = s; scaleY = s
                                translationY = -16.dp.toPx() * progress
                            }
                        )
                        Text(
                            label, color = labelTint, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** Small square header button (theme, unit, language). */
@Composable
fun HeaderButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 3.dp).height(36.dp).widthIn(min = 44.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.onBackground),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground)
    ) { content() }
}
