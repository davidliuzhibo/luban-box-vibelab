package com.vrgames.lubanbox.content

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.vrgames.lubanbox.core.Relics
import com.vrgames.lubanbox.game.Game

/** Attachment panel ids of the scene labels and panels (placed by the bench). */
const val LABEL_FIRST = "coach_first"
const val PANEL_INTRO = "intro"
const val PANEL_RESULT = "result"
const val PANEL_MENU = "menu"
const val PANEL_RELIC = "relic_card"
const val PANEL_HELP = "help"

/** Inner size (w, h in meters) of each rosewood frame in assets/panels/panel_<id>.glb; the text must fit inside. */
val FRAME_SIZE = mapOf(
    PANEL_INTRO to (0.54f to 0.44f),
    PANEL_RESULT to (0.48f to 0.4f),
    PANEL_RELIC to (0.42f to 0.32f),
    PANEL_MENU to (0.42f to 0.5f),
    PANEL_HELP to (0.36f to 0.22f),
)

/** Text height in the scene, so the labels read at arm's length to about 1.5 m. */
private const val TEXT_METERS = 0.022f

private val LabelInk = Color(0xFFF6EEDF)
private val Panel = Color(0xCC24160C)

/**
 * A short instruction floating in the scene next to what it is about, with an animated pinch
 * icon. Sized in meters through [converter], so it stays the same physical size on any panel
 * density. [onPinch]: the label itself is pinchable (the burn label fires the engine).
 */
@Composable
fun CoachLabel(text: String, accent: Color, converter: PhysicalLengthConverter, onPinch: (() -> Unit)? = null) {
    fun m(meters: Float): Dp = converter.lengthToDp(meters, LengthUnit.Meters)
    val shape = RoundedCornerShape(m(0.02f))
    var mod = Modifier
        .widthIn(max = m(0.4f))
        .clip(shape)
        .background(Panel)
        .border(m(0.002f), accent, shape)
    if (onPinch != null) mod = mod.clickable { onPinch() }
    Row(
        mod.padding(horizontal = m(0.016f), vertical = m(0.01f)),
        horizontalArrangement = Arrangement.spacedBy(m(0.012f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PinchIcon(accent, m(0.034f))
        Text(text, fontSize = m(TEXT_METERS).value.sp, color = LabelInk)
    }
}

/** Thumb and index tip closing on each other, with a flash when they meet. */
@Composable
private fun PinchIcon(accent: Color, size: Dp) {
    val t by rememberInfiniteTransition(label = "pinch").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "pinchT",
    )
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        // Close over the first half, touch, then open again.
        val close = when {
            t < 0.45f -> t / 0.45f
            t < 0.7f -> 1f
            else -> 1f - (t - 0.7f) / 0.3f
        }
        val gap = w * (0.32f - 0.27f * close)
        val tip = Offset(w * 0.62f, w * 0.5f)
        val palm = Offset(w * 0.12f, w * 0.5f)
        val index = Offset(tip.x, tip.y - gap)
        val thumb = Offset(tip.x, tip.y + gap)
        val stroke = w * 0.07f
        drawLine(accent.copy(alpha = 0.7f), palm, index, strokeWidth = stroke)
        drawLine(accent.copy(alpha = 0.7f), palm, thumb, strokeWidth = stroke)
        drawCircle(accent, w * 0.1f, index)
        drawCircle(accent, w * 0.1f, thumb)
        if (close >= 1f) drawCircle(accent.copy(alpha = 0.6f), w * 0.3f, tip, style = Stroke(stroke))
    }
}

/** First time on the bench: how to handle a relic at all. */
@Composable
fun FirstLabel(converter: PhysicalLengthConverter) =
    CoachLabel("用拇指和食指捏住一块木板，拖动它", Color(0xFFE9C46A), converter)

/**
 * Text on the dark lacquer inside a carved rosewood frame (the frame model is added by the stage).
 * The frame's size is fixed: content taller than it is scaled down to fit, so nothing slips under the edge.
 */
@Composable
private fun Framed(c: PhysicalLengthConverter, id: String, content: @Composable () -> Unit) {
    fun m(meters: Float): Dp = c.lengthToDp(meters, LengthUnit.Meters)
    val (w, h) = FRAME_SIZE.getValue(id)
    Layout(
        content = { Column(verticalArrangement = Arrangement.spacedBy(m(0.008f)), horizontalAlignment = Alignment.CenterHorizontally) { content() } },
        modifier = Modifier.width(m(w)).height(m(h)).padding(horizontal = m(0.02f), vertical = m(0.012f)),
    ) { measurables, constraints ->
        val p = measurables[0].measure(constraints.copy(minWidth = constraints.maxWidth, minHeight = 0, maxHeight = Constraints.Infinity))
        val k = if (p.height > constraints.maxHeight) constraints.maxHeight.toFloat() / p.height else 1f
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = ((constraints.maxWidth - p.width * k) / 2f).roundToInt().coerceAtLeast(0)
            val y = ((constraints.maxHeight - p.height * k) / 2f).roundToInt().coerceAtLeast(0)
            p.placeWithLayer(x, y) { scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0f, 0f) }
        }
    }
}

private val ButtonBg = Color(0xFF3A2414)
private val ButtonLine = Color(0xFF9A7A44)
private val GoldInk = Color(0xFF2A1C0C)

/** The step that moves the game on: the one gold button on a panel. */
@Composable
private fun GoldButton(c: PhysicalLengthConverter, text: String, glow: Boolean = false, onClick: () -> Unit) {
    fun m(meters: Float): Dp = c.lengthToDp(meters, LengthUnit.Meters)
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(
        0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "glowT",
    )
    val shape = RoundedCornerShape(m(0.012f))
    val edge = if (glow) Color(0xFFFFF4C0).copy(alpha = 0.5f + 0.5f * pulse) else Color(0xFFFFF0C8)
    Text(
        text,
        Modifier.clip(shape).background(Gold).border(m(if (glow) 0.004f else 0.002f), edge, shape).clickable { onClick() }
            .padding(horizontal = m(0.028f), vertical = m(0.013f)),
        fontSize = m(0.024f).value.sp,
        color = GoldInk,
    )
}

@Composable
private fun PlainButton(c: PhysicalLengthConverter, text: String, color: Color = LabelInk, onClick: () -> Unit) {
    fun m(meters: Float): Dp = c.lengthToDp(meters, LengthUnit.Meters)
    val shape = RoundedCornerShape(m(0.01f))
    Text(
        text,
        Modifier.clip(shape).background(ButtonBg).border(m(0.0015f), ButtonLine, shape).clickable { onClick() }
            .padding(horizontal = m(0.016f), vertical = m(0.01f)),
        fontSize = m(0.019f).value.sp,
        color = color,
    )
}

@Composable
private fun Line(c: PhysicalLengthConverter, text: String, size: Float = 0.019f, color: Color = LabelInk) =
    Text(text, Modifier.fillMaxWidth(), fontSize = c.lengthToDp(size, LengthUnit.Meters).value.sp, color = color, textAlign = TextAlign.Center)

/** 「第 3 / 12 件」 */
private fun countOf(id: String) = "第 ${Relics.all.indexOfFirst { it.id == id } + 1} / ${Relics.all.size} 件"

/** The title card over the back of the bench: what the game is, how to handle a relic, and start. */
@Composable
fun IntroPanel(converter: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion
    val p = Game.workshop.progress
    val done = Relics.all.count { p.solved(it.id) }
    Framed(converter, PANEL_INTRO) {
        Line(converter, "鲁班机关匣", 0.044f, Gold)
        Line(converter, "木 作 · 铜 作 · 天 工　　共 ${Relics.all.size} 件器物", 0.018f, Dim)
        Line(converter, "工作台上摆着一件件古代机关，找出打开它们的方法。")
        Line(converter, "① 用拇指和食指捏住木板、木块或铜环（两根手指捏，不要整只手抓）", 0.017f)
        Line(converter, "② 保持捏住，推、拉、拨、拖到位置；两根手指张开就放下了", 0.017f)
        Line(converter, "卡住了，台面右前方有「提示」「演示一步」「跳过这件」。", 0.017f, Dim)
        GoldButton(converter, if (done == 0) "开始第 1 件" else "继续（已完成 $done / ${Relics.all.size}）") { Game.startWork() }
        Line(converter, DISCLAIMER, 0.0115f, Dim)
    }
}

/** The relic just opened: the token, how it went, then "next relic" as the one gold button. */
@Composable
fun ResultPanel(converter: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion
    val relic = Relics.byId(Game.relicId)
    val next = Game.workshop.next()
    val p = Game.workshop.progress
    Framed(converter, PANEL_RESULT) {
        Line(converter, "打开了！得到「${relic.token}」", 0.03f, Gold)
        Line(converter, if (p.unaided(relic.id)) "没看一次提示：巧手！" else "用了 ${Game.hintsUsed} 次提示。不看提示再开一次，就是「巧手」。", 0.018f, Dim)
        if (p.chapterDone(relic.chapter)) {
            val reward = Relics.chapterRewards[relic.chapter]
            Line(converter, if (reward != null) "${Relics.chapterTitles[relic.chapter]}完成，集齐了「$reward」。" else "总机关匣打开了。全部完成，谢谢你，巧手的匠人。")
        }
        if (next != null) GoldButton(converter, "下一件：${next.title} →") { Game.nextRelic() }
        Row(horizontalArrangement = Arrangement.spacedBy(converter.lengthToDp(0.012f, LengthUnit.Meters))) {
            PlainButton(converter, if (Game.speaking == "story_${relic.id}") "停止讲解" else "听讲解") {
                if (Game.speaking == "story_${relic.id}") Game.stopVoice() else Game.speak("story_${relic.id}")
            }
            PlainButton(converter, "再来一次") { Game.restart() }
        }
        val skipped = Relics.all.filter { p.skipped(it.id) }
        if (skipped.isNotEmpty()) Line(converter, "跳过的：${skipped.joinToString("、") { it.title }}（营造图谱里可以回去做）", 0.015f, Dim)
    }
}

/**
 * The way back to everything once the windows are closed: a small "menu" tab on the bench that
 * unfolds into the relic's card, the two windows, starting over and quitting.
 */
@Composable
fun MenuPanel(converter: PhysicalLengthConverter, openNotes: () -> Unit, openAtlas: () -> Unit, quit: () -> Unit, faceMe: () -> Unit) {
    if (!Game.menuOpen) {
        CoachLabel("菜单", Dim, converter) { Game.menuOpen = true }
        return
    }
    Framed(converter, PANEL_MENU) {
        Line(converter, "菜单　${countOf(Game.relicId)}", 0.024f, Gold)
        PlainButton(converter, "这件器物的说明") { Game.relicCardDismissed = false; Game.menuOpen = false }
        PlainButton(converter, "营造图谱：选器物、看来历") { openAtlas(); Game.menuOpen = false }
        PlainButton(converter, "匠人手记：台高、环境、音乐") { openNotes(); Game.menuOpen = false }
        PlainButton(converter, "工作台摆到我正前方") { faceMe(); Game.menuOpen = false }
        PlainButton(converter, "从头再来这一件") { Game.restart(); Game.menuOpen = false }
        PlainButton(converter, "退出游戏", Color(0xFFF08A6A)) { quit() }
        GoldButton(converter, "返回") { Game.menuOpen = false }
        Line(converter, "进度自动保存：每打开一件都会记下来。", 0.014f, Dim)
    }
}

/** The relic on the bench: its name, what opens it and how to handle it; until the first move. */
@Composable
fun RelicCard(converter: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion
    val relic = Relics.byId(Game.relicId)
    val round = if (relic.rounds > 1) "　第 ${Game.round + 1} / ${relic.rounds} 局" else ""
    Framed(converter, PANEL_RELIC) {
        Line(converter, "${relic.title}$round", 0.028f, Gold)
        Line(converter, "${Relics.chapterTitles[relic.chapter]}　${countOf(relic.id)}", 0.015f, Dim)
        Line(converter, relic.goal, 0.018f)
        Line(converter, howTo(relic.id), 0.015f, Dim)
        GoldButton(converter, "知道了") { Game.relicCardDismissed = true }
    }
}

/**
 * Always on the bench's front right while a relic is out: the hint ladder, a solver move, and
 * skipping. When the player seems stuck, it says so and the hint button glows (no arrow appears
 * until they ask).
 */
@Composable
fun HelpPanel(converter: PhysicalLengthConverter) {
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion
    val ideaGiven = Game.hintsUsed > 0
    Framed(converter, PANEL_HELP) {
        Line(
            converter,
            Game.note ?: if (Game.offerHelp) "好像卡住了？要不要提示？" else "${Relics.byId(Game.relicId).title}　${countOf(Game.relicId)}",
            if (Game.note != null) 0.016f else 0.019f,
            if (Game.offerHelp && Game.note == null) Gold else LabelInk,
        )
        GoldButton(converter, if (!ideaGiven) "提示" else if (Game.hint == null) "再给提示" else "再具体一点", glow = Game.offerHelp) { Game.askHint() }
        Row(horizontalArrangement = Arrangement.spacedBy(converter.lengthToDp(0.012f, LengthUnit.Meters))) {
            if (ideaGiven) PlainButton(converter, "演示一步") { Game.demoStep() }
            PlainButton(converter, "跳过这件", Dim) { Game.skip() }
        }
    }
}
