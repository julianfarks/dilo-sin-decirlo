package app.dilosindecirlo.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate as rotateDraw
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dilosindecirlo.ui.theme.Dsd
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Botón grande con sombra desplazada. [debounceMs] evita dobles pulsaciones accidentales. */
@Composable
fun BrutalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Dsd.Blue,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    fontSize: TextUnit = 20.sp,
    height: Dp = 60.dp,
    debounceMs: Long = 250L,
) {
    val shape = RoundedCornerShape(18.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var lastClick by remember { mutableLongStateOf(0L) }
    val press = if (pressed) 3.dp else 0.dp
    Box(modifier.height(height)) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 4.dp, top = 4.dp)
                .background(Dsd.Ink, shape),
        )
        Box(
            Modifier
                .fillMaxSize()
                .padding(end = 4.dp, bottom = 4.dp)
                .offset(x = press, y = press)
                .clip(shape)
                .background(if (enabled) color else Dsd.CreamDark)
                .border(3.dp, Dsd.Ink, shape)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastClick >= debounceMs) {
                        lastClick = now
                        onClick()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (enabled) contentColor else Dsd.Grey,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                lineHeight = fontSize * 1.1f,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
    }
}

/** Contenedor con borde grueso y sombra desplazada, al estilo de las cartas de cartón. */
@Composable
fun BrutalCard(
    modifier: Modifier = Modifier,
    color: Color = Dsd.Paper,
    cornerRadius: Dp = 24.dp,
    shadow: Dp = 5.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .padding(start = shadow, top = shadow)
                .background(Dsd.Ink, shape),
        )
        val inner = Modifier
            .padding(end = shadow, bottom = shadow)
            .clip(shape)
            .background(color)
            .border(3.dp, Dsd.Ink, shape)
        Box(
            if (onClick != null) inner.clickable(onClick = onClick) else inner,
            content = content,
        )
    }
}

@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Dsd.Cream)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                BrutalButton("←", onBack, Modifier.width(60.dp), color = Dsd.Paper, contentColor = Dsd.Ink, height = 50.dp, fontSize = 22.sp)
                Spacer(Modifier.width(12.dp))
            }
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Black, color = Dsd.Ink, modifier = Modifier.weight(1f))
            actions()
        }
        content()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Dsd.Blue,
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.5.sp,
        modifier = modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

@Composable
fun StepperRow(
    label: String,
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Dsd.Ink)
            if (hint != null) Text(hint, fontSize = 12.sp, color = Dsd.Grey)
        }
        BrutalButton("−", onMinus, Modifier.width(52.dp), color = Dsd.Paper, contentColor = Dsd.Ink, height = 46.dp, fontSize = 22.sp, enabled = minusEnabled, debounceMs = 80)
        Text(
            valueText,
            modifier = Modifier.width(70.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            color = Dsd.Ink,
        )
        BrutalButton("+", onPlus, Modifier.width(52.dp), color = Dsd.Paper, contentColor = Dsd.Ink, height = 46.dp, fontSize = 22.sp, enabled = plusEnabled, debounceMs = 80)
    }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = Dsd.Blue,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) selectedColor else Dsd.Paper)
            .border(2.5.dp, Dsd.Ink, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Color.White else Dsd.Ink,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) -> Chip(label, value == selected, { onSelect(value) }) }
    }
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Dsd.Ink)
            if (description != null) Text(description, fontSize = 12.sp, color = Dsd.Grey, lineHeight = 15.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Dsd.Blue,
                checkedBorderColor = Dsd.Ink,
                uncheckedThumbColor = Dsd.Ink,
                uncheckedTrackColor = Dsd.CreamDark,
                uncheckedBorderColor = Dsd.Ink,
            ),
        )
    }
}

/** Texto que reduce su tamaño hasta caber en una línea (palabras largas en pantallas pequeñas). */
@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit,
    minFontSize: TextUnit = 16.sp,
    color: Color = Dsd.Ink,
    fontWeight: FontWeight = FontWeight.Black,
    textAlign: TextAlign = TextAlign.Center,
) {
    var size by remember(text, maxFontSize) { mutableStateOf(maxFontSize) }
    var ready by remember(text, maxFontSize) { mutableStateOf(false) }
    Text(
        text = text,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        color = color,
        fontSize = size,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { result ->
            if (result.didOverflowWidth && size.value > minFontSize.value) {
                size = (size.value * 0.92f).sp
            } else {
                ready = true
            }
        },
    )
}

/** Logotipo original: tres bloques inclinados y la cara con la boca tapada. */
@Composable
fun Logo(modifier: Modifier = Modifier, scale: Float = 1f) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🤐", fontSize = (44 * scale).sp)
        Spacer(Modifier.height(2.dp))
        LogoBlock("¡DILO", Dsd.Blue, Color.White, (46 * scale).sp, -3f)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoBlock("SIN", Dsd.Red, Color.White, (24 * scale).sp, 3f)
            Spacer(Modifier.width(8.dp))
            LogoBlock("DECIRLO!", Dsd.Yellow, Dsd.Ink, (34 * scale).sp, -2f)
        }
    }
}

@Composable
private fun LogoBlock(text: String, bg: Color, fg: Color, fontSize: TextUnit, rotation: Float) {
    BrutalCard(Modifier.rotate(rotation), color = bg, cornerRadius = 14.dp, shadow = 4.dp) {
        Text(
            text,
            color = fg,
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
        )
    }
}

/** Fondo con círculos que suben despacio. Una sola animación y solo se redibuja el lienzo. */
@Composable
fun AnimatedBackground(enabled: Boolean, modifier: Modifier = Modifier) {
    val progress: State<Float> = if (enabled) {
        rememberInfiniteTransition(label = "bg").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
            label = "bgProgress",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    val colors = listOf(Dsd.Blue.copy(alpha = 0.10f), Dsd.Red.copy(alpha = 0.09f), Dsd.Yellow.copy(alpha = 0.22f))
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        val count = 10
        for (i in 0 until count) {
            val phase = (t + i.toFloat() / count) % 1f
            val x = size.width * ((i * 0.381f + 0.12f) % 1f) + sin(phase * 2f * PI.toFloat() + i) * 18.dp.toPx()
            val y = size.height * (1.15f - phase * 1.3f)
            val radius = (28 + (i % 4) * 22).dp.toPx()
            drawCircle(colors[i % colors.size], radius, Offset(x, y))
        }
    }
}

/** Lluvia de confeti para la pantalla de victoria. */
@Composable
fun Confetti(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val progress: State<Float> = if (enabled) {
        rememberInfiniteTransition(label = "confetti").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Restart),
            label = "confettiProgress",
        )
    } else {
        remember { mutableFloatStateOf(0.3f) }
    }
    val palette = listOf(Dsd.Blue, Dsd.Red, Dsd.Yellow, Dsd.Green)
    val particles = remember {
        val r = Random(7)
        List(46) { Triple(r.nextFloat(), 0.4f + r.nextFloat() * 0.9f, r.nextFloat()) }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        particles.forEachIndexed { i, (x0, speed, offset) ->
            val y = ((t * speed + offset) % 1f) * (size.height + 40.dp.toPx()) - 20.dp.toPx()
            val x = size.width * x0 + sin((t * 6f + offset * 10f)) * 14.dp.toPx()
            rotateDraw(degrees = (t * 360f * speed + offset * 90f), pivot = Offset(x, y)) {
                drawRect(
                    color = palette[i % palette.size],
                    topLeft = Offset(x, y),
                    size = Size(9.dp.toPx(), 15.dp.toPx()),
                )
            }
        }
    }
}
