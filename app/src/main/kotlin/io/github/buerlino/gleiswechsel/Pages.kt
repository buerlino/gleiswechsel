package io.github.buerlino.gleiswechsel

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The global offset of the track switch time, from the official one at every station the rider
 * hasn't set, shown with a − (user, 2026-10-06) and saved without it, coloured against 0, the
 * official time. And whether the Optimization panel is on (user, 2026-10-06); off, the times set
 * there aren't used.
 */
@Composable
internal fun Settings(
    offset: String,
    optimize: Boolean,
    enabled: Boolean,
    onOffset: (String) -> Unit,
    onOptimize: (Boolean) -> Unit,
    onBack: () -> Unit,
) = SubPage(stringResource(R.string.settings), onBack) {
    MinutesStepper(
        stringResource(R.string.global_offset), offset.toLongOrNull()?.let { -it }, -DEFAULT_OFFSET, 0, enabled,
        range = -99L..0, arrows = false,
    ) { onOffset(it?.let { "${-it}" } ?: "") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.optimization_setting), Modifier.weight(1f).padding(end = 16.dp))
        Switch(optimize, onOptimize, enabled = enabled)
    }
}

/** One topic each, folded until tapped (user, 2026-10-06: short, friendly, not overwhelming). */
@Composable
internal fun Help(onBack: () -> Unit) = SubPage(stringResource(R.string.help), onBack) {
    help.forEach { (emoji, heading, text) ->
        var open by remember { mutableStateOf(false) }
        Column {
            Heading("$emoji  ${stringResource(heading)}", open) { open = !open }
            AnimatedVisibility(open) { Text(stringResource(text), Modifier.padding(top = 4.dp)) }
        }
    }
}

private val help = listOf(
    Triple("🚆", R.string.help_what, R.string.help_what_text),
    Triple("⏱️", R.string.help_switch, R.string.help_switch_text),
    Triple("🎨", R.string.help_colours, R.string.help_colours_text),
    Triple("📈", R.string.help_efficient, R.string.help_efficient_text),
    Triple("🎫", R.string.help_tickets, R.string.help_tickets_text),
    Triple("⚠️", R.string.help_careful, R.string.help_careful_text),
    Triple("📡", R.string.help_data, R.string.help_data_text),
)

/** A page with a back arrow and its [title]; the [content] scrolls. */
@Composable
private fun SubPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("←", Modifier.spokenAs(stringResource(R.string.back)), fontSize = 22.sp) }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
