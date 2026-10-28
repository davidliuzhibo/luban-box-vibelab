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
    CoachLabel("用拇指和食指捏住一根木条，拖动它", Color(0xFFE9C46A), converter)

/**
 * The title card, floating over the back of the bench (the 匠人手记 window sits wherever the
 * system puts it, often too far to read): what the game is, how to handle a relic, and a
 * pinchable start.
 */
@Composable
fun IntroPanel(converter: PhysicalLengthConverter) {
    fun m(meters: Float): Dp = converter.lengthToDp(meters, LengthUnit.Meters)
    fun size(meters: Float) = m(meters).value.sp
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion
    val fresh = Relics.all.none { Game.workshop.progress.solved(it.id) }
    val shape = RoundedCornerShape(m(0.025f))
    Column(
        Modifier.width(m(0.52f)).clip(shape).background(Panel).border(m(0.002f), Gold, shape)
            .padding(horizontal = m(0.03f), vertical = m(0.024f)),
        verticalArrangement = Arrangement.spacedBy(m(0.012f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("鲁班机关匣", fontSize = size(0.045f), color = Gold)
        Text("木 作 · 铜 作 · 天 工", fontSize = size(0.02f), color = Dim)
        Text("工作台上摆着一件件古代机关，找出打开它们的方法。", fontSize = size(0.022f), color = LabelInk)
        Column(verticalArrangement = Arrangement.spacedBy(m(0.006f))) {
            Text("怎么玩：", fontSize = size(0.022f), color = Gold)
            Text("① 用拇指和食指捏住木条、木块或铜环（两根手指捏，不要整只手去抓）", fontSize = size(0.02f), color = LabelInk)
            Text("② 保持捏住，推、拉、拨、拖到位置", fontSize = size(0.02f), color = LabelInk)
            Text("③ 两根手指张开，就放下了", fontSize = size(0.02f), color = LabelInk)
            Text("卡住了可以捏台面左边的「菜单」要提示；进度自动保存", fontSize = size(0.017f), color = Dim)
        }
        CoachLabel(if (fresh) "捏一下这里：开始" else "捏一下这里：继续", Gold, converter) { Game.startWork() }
        Text(DISCLAIMER, fontSize = size(0.0125f), color = Dim)
    }
}

/** The relic just opened: the token, then "next relic" and "again" as big pinchable lines. */
@Composable
fun ResultPanel(converter: PhysicalLengthConverter) {
    fun m(meters: Float): Dp = converter.lengthToDp(meters, LengthUnit.Meters)
    fun size(meters: Float) = m(meters).value.sp
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion
    val relic = Relics.byId(Game.relicId)
    val next = Game.workshop.next()
    val shape = RoundedCornerShape(m(0.025f))
    Column(
        Modifier.width(m(0.46f)).clip(shape).background(Panel).border(m(0.002f), Gold, shape)
            .padding(horizontal = m(0.026f), vertical = m(0.02f)),
        verticalArrangement = Arrangement.spacedBy(m(0.012f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("打开了！得到「${relic.token}」", fontSize = size(0.032f), color = Gold)
        if (Game.workshop.progress.chapterDone(relic.chapter)) {
            val reward = Relics.chapterRewards[relic.chapter]
            Text(if (reward != null) "${Relics.chapterTitles[relic.chapter]}完成，集齐了「$reward」。" else "总机关匣打开了。全部完成，谢谢你，巧手的匠人。",
                fontSize = size(0.02f), color = LabelInk)
        }
        if (next != null) CoachLabel("下一件：${next.title} →", Gold, converter) { Game.nextRelic() }
        CoachLabel("再来一次", Dim, converter) { Game.restart() }
        Text("它的来历已经写进营造图谱（菜单里打开）。", fontSize = size(0.016f), color = Dim)
    }
}

/**
 * The way back to everything once the windows are closed: a small "menu" tab on the bench that
 * unfolds into a hint, the two windows, starting over and quitting.
 */
@Composable
fun MenuPanel(converter: PhysicalLengthConverter, openNotes: () -> Unit, openAtlas: () -> Unit, quit: () -> Unit, faceMe: () -> Unit) {
    fun m(meters: Float): Dp = converter.lengthToDp(meters, LengthUnit.Meters)
    if (!Game.menuOpen) {
        CoachLabel("菜单", Dim, converter) { Game.menuOpen = true }
        return
    }
    val shape = RoundedCornerShape(m(0.02f))
    Column(
        Modifier.width(m(0.42f)).clip(shape).background(Panel).border(m(0.002f), Dim, shape).padding(m(0.016f)),
        verticalArrangement = Arrangement.spacedBy(m(0.01f)),
    ) {
        CoachLabel(if (Game.hint == null) "给点提示" else "再具体一点", Gold, converter) { Game.askHint(); Game.menuOpen = false }
        CoachLabel("这件器物的说明", Gold, converter) { Game.relicCardDismissed = false; Game.menuOpen = false }
        CoachLabel("匠人手记：台高、环境、音乐", Gold, converter) { openNotes(); Game.menuOpen = false }
        CoachLabel("营造图谱：选器物、看来历", Gold, converter) { openAtlas(); Game.menuOpen = false }
        CoachLabel("工作台摆到我正前方", Gold, converter) { faceMe(); Game.menuOpen = false }
        CoachLabel("从头再来", Dim, converter) { Game.restart(); Game.menuOpen = false }
        Text("进度自动保存：每打开一件都会记下来。", fontSize = m(0.016f).value.sp, color = Dim)
        CoachLabel("退出游戏", Color(0xFFE0663F), converter) { quit() }
        CoachLabel("收起菜单", Dim, converter) { Game.menuOpen = false }
    }
}

/** The relic on the bench: its name, what opens it and how to handle it; until the first move. */
@Composable
fun RelicCard(converter: PhysicalLengthConverter) {
    fun m(meters: Float): Dp = converter.lengthToDp(meters, LengthUnit.Meters)
    fun size(meters: Float) = m(meters).value.sp
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion
    val relic = Relics.byId(Game.relicId)
    val round = if (relic.rounds > 1) "　第 ${Game.round + 1} / ${relic.rounds} 局" else ""
    val shape = RoundedCornerShape(m(0.022f))
    Column(
        Modifier.width(m(0.38f)).clip(shape).background(Panel).border(m(0.002f), Gold, shape)
            .padding(horizontal = m(0.022f), vertical = m(0.018f)),
        verticalArrangement = Arrangement.spacedBy(m(0.008f)),
    ) {
        Text("${relic.title}$round", fontSize = size(0.028f), color = Gold)
        Text(relic.goal, fontSize = size(0.018f), color = LabelInk)
        Text(howTo(relic.id), fontSize = size(0.016f), color = Dim)
        CoachLabel("知道了", Gold, converter) { Game.relicCardDismissed = true }
    }
}
