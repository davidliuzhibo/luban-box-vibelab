package com.vrgames.lubanbox.content

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.Text
import com.vrgames.lubanbox.core.Relics
import com.vrgames.lubanbox.game.Game

/**
 * 营造图谱: every relic of the chapter (open ones can be picked), the 鲁班尺 sections collected so
 * far, and the history of each relic once it has been opened.
 */
@Composable
fun AtlasWindow() {
    @Suppress("UNUSED_VARIABLE") val pv = Game.progressVersion
    val progress = Game.workshop.progress
    var shown by remember { mutableStateOf<String?>(null) }
    val selected = Relics.byId(shown ?: Game.relicId)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("营造图谱", fontSize = 30.sp, color = Gold)
        // Chapter tabs, then the relics of the chapter shown.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in 1..4) {
                val open = progress.chapterOpen(c)
                Button(onClick = { if (open) shown = Relics.chapter(c).first().id }) {
                    Text(if (open) (if (c == selected.chapter) "● " else "") + Relics.chapterTitles[c]!!.substringAfter("· ") else "🔒", fontSize = 18.sp)
                }
            }
        }
        Text(Relics.chapterTitles[selected.chapter] ?: "", fontSize = 21.sp, color = Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Relics.all.filter { it.chapter == selected.chapter }.forEach { r ->
                val mark = when {
                    progress.skipped(r.id) -> "○ "
                    progress.solved(r.id) -> if (progress.unaided(r.id)) "◆ " else "✓ "
                    r.id == Game.relicId -> "▶ "
                    else -> ""
                }
                Button(onClick = { shown = r.id }) {
                    Text(if (progress.open(r)) mark + r.title else "🔒", fontSize = 19.sp)
                }
            }
        }
        for (c in 1..3) {
            val have = Relics.chapter(c).count { progress.solved(it.id) }
            val all = Relics.chapter(c).size
            Text("${Relics.chapterRewards[c]}：$have / $all" + if (have == all) "（已集齐）" else "", fontSize = 19.sp, color = Gold)
        }

        Section {
            Text(selected.title, fontSize = 27.sp, color = Ink)
            // Line drawing of the relic (tools/make_atlas.py); shown once it has been opened.
            val context = LocalContext.current
            val drawing = remember(selected.id) { context.resources.getIdentifier("atlas_${selected.id}", "drawable", context.packageName) }
            if (drawing != 0 && progress.solved(selected.id)) {
                Image(
                    painterResource(drawing),
                    contentDescription = selected.title,
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                )
            }
            if (progress.solved(selected.id)) {
                Text(selected.story, fontSize = 21.sp, color = Ink)
                selected.facts.forEach { Text("· $it", fontSize = 19.sp, color = Dim) }
                if (progress.unaided(selected.id)) Text("◆ 巧手：没有看提示就打开了它。", fontSize = 18.sp, color = Gold)
                if (progress.skipped(selected.id)) Text("○ 这一件跳过了。在台上选它，就能回去再做。", fontSize = 18.sp, color = Gold)
            } else {
                Text(selected.goal, fontSize = 21.sp, color = Ink)
                Text("打开它以后，这里会写上它的来历。", fontSize = 18.sp, color = Dim)
            }
            if (selected.id != Game.relicId && progress.open(selected)) {
                Button(onClick = {
                    Game.select(selected.id)
                    shown = null
                }) { Text("放到工作台上") }
            }
        }
    }
}
