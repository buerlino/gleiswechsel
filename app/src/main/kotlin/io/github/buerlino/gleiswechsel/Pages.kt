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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The global offset of the track switch time, from the official one at every station the rider
 * hasn't set, shown with a − (user, 2026-10-06). Coloured against 0, the official time.
 */
@Composable
internal fun Settings(offset: String, enabled: Boolean, onOffset: (String) -> Unit, onBack: () -> Unit) =
    SubPage(stringResource(R.string.settings), onBack) {
        MinutesField(stringResource(R.string.global_offset), offset, DEFAULT_OFFSET, 0, enabled, minus = true, onValueChange = onOffset)
    }

@Composable
internal fun Help(onBack: () -> Unit) = SubPage(stringResource(R.string.help), onBack) {
    help.forEach { (heading, text) ->
        Column {
            Text(stringResource(heading), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(text))
        }
    }
}

private val help = listOf(
    R.string.help_what to R.string.help_what_text,
    R.string.help_switch to R.string.help_switch_text,
    R.string.help_efficient to R.string.help_efficient_text,
    R.string.help_careful to R.string.help_careful_text,
    R.string.help_data to R.string.help_data_text,
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
