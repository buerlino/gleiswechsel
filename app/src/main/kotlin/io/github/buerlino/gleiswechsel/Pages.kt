package io.github.buerlino.gleiswechsel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The offset below the official minimum, at every station the rider hasn't set (user, 2026-10-06). */
@Composable
internal fun Settings(offset: String, enabled: Boolean, onOffset: (String) -> Unit, onBack: () -> Unit) =
    SubPage("Settings", onBack) {
        MinutesField("Your change: official minimum minus", offset, DEFAULT_OFFSET, enabled, onOffset)
    }

@Composable
internal fun Help(onBack: () -> Unit) = SubPage("Help", onBack) {
    help.forEach { (heading, text) ->
        Column {
            Text(heading, style = MaterialTheme.typography.titleMedium)
            Text(text)
        }
    }
}

private val help = listOf(
    "What it does" to "Gleiswechsel looks for a faster trip than the official planner shows. " +
        "The planner offers a change only if it takes at least the station's official minimum, " +
        "e.g. 5 minutes at Luzern. If you change faster, an earlier train may still be yours.",
    "Your change" to "The minutes in a box: how long you need from one train to the next, walk included. " +
        "Everywhere it's the official minimum minus the minutes in Settings (⚙). " +
        "Under a result you can set your own at each station.",
    "Efficiency" to "The fastest trip's time divided by this one's: 100% is the fastest.",
    "Careful" to "Planned times only: a late train can make a short change impossible. " +
        "Whether you make it is up to you.",
    "Data" to "Connections: transport.opendata.ch. Official minimums: opentransportdata.swiss, " +
        "timetable 2026. Not affiliated with SBB.",
)

/** A page with a back arrow and its [title]; the [content] scrolls. */
@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("←", fontSize = 22.sp) }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
