@file:OptIn(ExperimentalMaterial3Api::class)

package com.topocalcbtp

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import kotlin.math.abs

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
    /** Sliding bubble of the active bottom tab and its content (light: black/white, dark: white/navy). */
    val navBox: Color,
    val navContent: Color,
    /** Bottom bar background (dark: dark grey, light: light grey). */
    val navBar: Color
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(false, Color.White, Brand.Navy, Color(0xFF52606D), Color(0xFF1E7B4F), Color.White, Brand.Ink,
        Color(0xFFD9E2EC), Brand.Navy, Color(0xFF111418), Color.White, Color(0xFFE4E7EC))
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
    Color(0xFFD9E2EC), Brand.Navy, Color(0xFF111418), Color.White, Color(0xFFE4E7EC))
private val DarkApp = AppColors(true, Color(0xFF0B1120), Color(0xFF9DB2FF), Color(0xFF9AA6BF), Color(0xFF4FD18B), Color.White, Brand.Ink,
    Color.White, Brand.Yellow, Color.White, Brand.Navy, Color(0xFF232937))

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

object IkIcons {
    /** Automatic level: eyepiece (left), telescope body with sight, wide objective (right), base, tripod. */
    val Level = strokeIcon("level", 1.8f,
        "M3 9.5h2.5V8H15V6.5h5v7h-5V12.5H5.5V11H3z", "M9 8V5.8h3.2V8",
        "M8 15.5h8M12 12.5v3M12 15.5V22M12 15.5L8 22M12 15.5l4 6.5")
    /** Theodolite: telescope tilted between the two standards of the alidade fork, tribrach, tripod. */
    val Theodolite = strokeIcon("theodolite", 1.8f,
        "M5 3v10h14V3", "M7.5 7.4L14.7 4l1.8 3.6-7.2 3.4z",
        "M8.5 15.8h7M12 13v2.8M12 15.8V22M12 15.8L8.5 22M12 15.8l3.5 6.2")
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

/**
 * Equal, fixed-width tabs. One circular bubble (drawn behind the tabs) slides to the selected tab with a
 * slightly springy animation and rises a little above the bar; the active icon sits inside it. Only a float
 * read in draw / graphicsLayer changes, nothing is re-measured, so the bar does not shake. In RTL tab 0 is on
 * the right, so the bubble's visual index is mirrored.
 */
@Composable
fun IkBottomNav(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalAppColors.current
    val n = labels.size
    val pos by animateFloatAsState(
        selected.toFloat(), animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f), label = "bubble"
    )
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(Modifier.fillMaxWidth().background(c.navBar)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp).height(62.dp)) {
            Box(Modifier.matchParentSize().drawBehind {
                val tabW = size.width / n
                val visual = if (rtl) (n - 1) - pos else pos
                drawCircle(c.navBox, radius = 25.dp.toPx(), center = Offset((visual + 0.5f) * tabW, 17.dp.toPx()))
            })
            Row(Modifier.matchParentSize()) {
                labels.forEachIndexed { i, label ->
                    val p = (1f - abs(pos - i)).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Column(
                            Modifier.padding(bottom = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                IkIcons.Tabs[i], contentDescription = label, tint = lerp(c.muted, c.navContent, p),
                                modifier = Modifier.size(24.dp).graphicsLayer {
                                    val s = 1f + 0.1f * p
                                    scaleX = s; scaleY = s
                                    translationY = -7.dp.toPx() * p
                                }
                            )
                            Text(
                                label, color = lerp(c.muted, c.navBox, p), fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
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
