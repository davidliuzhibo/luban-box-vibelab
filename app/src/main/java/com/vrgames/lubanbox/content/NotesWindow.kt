package com.vrgames.lubanbox.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.vrgames.lubanbox.core.Relics
import com.vrgames.lubanbox.game.Game

val Ink = Color(0xFFF3E9DA)
val Cinnabar = Color(0xFFE0663F)
val Gold = Color(0xFFE9C46A)
val Dim = Color(0xFFC9B9A3)
private val Card = Color(0x33F3E9DA)

const val DISCLAIMER = "本作品中的器物为复原演绎，史料只陈述公认史实。音效取自 Kenney 的 Impact Sounds（CC0），木纹贴图取自 Poly Haven（CC0），字体为思源宋体（SIL OFL），背景音乐见素材说明。"

/** 匠人手记: the title card, then the relic at hand, hints, and bench settings. */
@Composable
fun NotesWindow() {
    if (Game.showTitle) TitleCard() else Notes()
}

@Composable
private fun TitleCard() {
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion
    val fresh = Relics.all.none { Game.workshop.progress.solved(it.id) }
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("鲁班机关匣", fontSize = 56.sp, color = Gold)
        Text("木 作 · 铜 作 · 天 工", fontSize = 25.sp, color = Dim)
        Text("工作台上摆着一件件古代机关。\n捏住木条、木块、木板，推、拉、拆，\n找出打开它们的方法。", fontSize = 23.sp, color = Ink)
        Button(onClick = { Game.startWork() }) { Text(if (fresh) "开始" else "继续", fontSize = 25.sp) }
        Spacer(Modifier.height(16.dp))
        Text(DISCLAIMER, fontSize = 18.sp, color = Dim)
    }
}

@Composable
private fun Notes() {
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion
    val relic = Relics.byId(Game.relicId)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("匠人手记", fontSize = 30.sp, color = Gold)
        val round = if (relic.rounds > 1) "　第 ${Game.round + 1} / ${relic.rounds} 局" else ""
        Text("${relic.title}$round", fontSize = 27.sp, color = Ink)
        Text(relic.goal, fontSize = 21.sp, color = Ink)
        Text(howTo(relic.id), fontSize = 19.sp, color = Dim)
        Text("已走 ${Game.moves} 步" + if (Game.hintsUsed > 0) "，看过 ${Game.hintsUsed} 次提示" else "", fontSize = 18.sp, color = Dim)

        Game.note?.let { NoteCard(it, Cinnabar) }

        if (Game.opened == relic.id) {
            NoteCard("打开了！得到「${relic.token}」。它的来历已经写进营造图谱。", Gold)
            val next = Game.workshop.next()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (next != null) Button(onClick = { Game.nextRelic() }) { Text("下一件：${next.title} →") }
                Button(onClick = { Game.restart() }) { Text("再来一次") }
            }
            if (Game.workshop.progress.chapterDone(relic.chapter)) {
                val reward = com.vrgames.lubanbox.core.Relics.chapterRewards[relic.chapter]
                if (reward != null) NoteCard("${com.vrgames.lubanbox.core.Relics.chapterTitles[relic.chapter]}完成，集齐了「$reward」。", Gold)
                else NoteCard("总机关匣打开了。木作、铜作、天工，全部完成。谢谢你，巧手的匠人。", Gold)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { Game.askHint() }) { Text(if (Game.hint == null) "给点提示" else "再具体一点") }
                Button(onClick = { Game.restart() }) { Text("从头再来") }
            }
            Game.hint?.let { h ->
                val text = when {
                    h.move != null && !h.showTarget -> "看工作台上的金色箭头：下一步动它，往箭头指的方向。"
                    h.move != null -> "半透明的影子就是它该去的位置。"
                    h.slot != null && !h.showTarget -> "金色箭头下的构件该装上去了。"
                    h.slot != null -> "它该装在「${h.slot.id}」，亮起的轮廓就是位置。"
                    else -> ""
                }
                NoteCard(text, Gold)
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("工作台", fontSize = 19.sp, color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { Game.raiseBench(-0.05f) }) { Text("降低") }
            Button(onClick = { Game.raiseBench(0.05f) }) { Text("升高") }
            Text("台面离地 ${(Game.benchHeight * 100).toInt()} 厘米", fontSize = 18.sp, color = Dim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { Game.toggleWorkshop() }) { Text(if (Game.workshopEnv) "环境：木作工坊" else "环境：我的房间") }
            Button(onClick = { Game.toggleMusic() }) { Text(if (Game.musicOn) "音乐：开" else "音乐：关") }
        }
        Text("鲁班锁、鲁班盒、花旗锁可以捏住转盘边缘左右拨动，转过来看背面。", fontSize = 18.sp, color = Dim)
    }
}

fun howTo(id: String) = when (id) {
    "lock" -> "捏住一根木条拖动：它会顺着你的手，沿能动的方向滑。推不动时会听到一声闷响。拆出来的木条会自己躺到下面的转盘上。"
    "klotski" -> "捏住木块拖动，只能直推，不能拿起来。"
    "box", "padlock" -> "捏住能动的部件推一推，有的能动一点，有的纹丝不动。"
    "wordlock" -> "捏住一个铜环上下拨动，它就转一格。"
    "mirror" -> "捏住铜镜左右拨动转向，上下拨动俯仰。"
    "armillary" -> "捏住四游环左右拨动，捏住窥管上下拨动。"
    else -> "捏起台上的构件，拖到该在的位置，靠近时会自动吸进去。"
}

@Composable
fun NoteCard(text: String, color: Color) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.18f)).padding(16.dp),
    ) {
        Text(text, fontSize = 21.sp, color = Ink)
    }
}

@Composable
fun Section(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
