package com.vrgames.lubanbox

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.vrgames.lubanbox.content.AtlasWindow
import com.vrgames.lubanbox.content.NotesWindow
import com.vrgames.lubanbox.content.WorkshopStage

const val ATLAS_ID = "Atlas"
const val NOTES_ID = "Notes"

/**
 * Three linked spaces: the workshop with the bench (Stage), the 营造图谱 (relics and their history)
 * and the 匠人手记 (the relic at hand, hints, bench settings). They share [com.vrgames.lubanbox.game.Game].
 */
fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme { WorkshopStage() }
        }
        WindowContainer(ATLAS_ID, Form.Planar, { defaultSize = WindowContainerSize(700.dp, 960.dp) }) {
            PicoTheme { AtlasWindow() }
        }
        WindowContainer(NOTES_ID, Form.Planar, { defaultSize = WindowContainerSize(640.dp, 960.dp) }) {
            PicoTheme { NotesWindow() }
        }
    }
