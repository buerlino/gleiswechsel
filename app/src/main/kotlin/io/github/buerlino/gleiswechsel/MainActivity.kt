package io.github.buerlino.gleiswechsel

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import io.github.buerlino.gleiswechsel.core.Connection
import io.github.buerlino.gleiswechsel.core.Find
import io.github.buerlino.gleiswechsel.core.Leg
import io.github.buerlino.gleiswechsel.core.Minimums
import io.github.buerlino.gleiswechsel.core.Stop
import io.github.buerlino.gleiswechsel.core.connections
import io.github.buerlino.gleiswechsel.core.search
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** How many minutes faster than the official minimum the rider changes, until set in Settings. */
internal const val DEFAULT_OFFSET = 1L

private sealed interface Result {
    data object Searching : Result
    /**
     * [connections]: how many official connections the search started from; [changes]: where they
     * change trains, each station once.
     */
    class Found(val day: LocalDateTime, val connections: Int, val changes: List<Stop>, val finds: List<Find>) : Result
    data object Failed : Result
}

private enum class Screen { SEARCH, SETTINGS, HELP }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The page is always light, so the bars' icons are dark, also on a phone in dark mode.
        val bars = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        val prefs = getSharedPreferences("commute", Context.MODE_PRIVATE)
        val minimums = Minimums(resources.openRawResource(R.raw.umsteigb).bufferedReader().use { it.readText() })
        setContent {
            MaterialTheme(lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App(prefs, minimums) }
            }
        }
    }
}

/**
 * The search page, or Help or Settings in its place. Every field is saved in [prefs] as it is typed:
 * the commute, the rider's transfer time at each change station (keyed by station id) and the
 * offset. Editing one clears the result; a time or the offset keeps the change stations.
 */
@Composable
private fun App(prefs: SharedPreferences, minimums: Minimums) {
    var screen by rememberSaveable { mutableStateOf(Screen.SEARCH) }
    var from by remember { mutableStateOf(prefs.getString("from", "")!!) }
    var to by remember { mutableStateOf(prefs.getString("to", "")!!) }
    var leaving by remember { mutableStateOf(prefs.getString("leaving", "")!!) }
    var offset by remember { mutableStateOf(prefs.getString("offset", "")!!) }
    var result by remember { mutableStateOf<Result?>(null) }
    var changes by remember { mutableStateOf(emptyList<Stop>()) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val time = parseTime(leaving)
    val searching = result == Result.Searching
    val offsetMinutes = offset.toLongOrNull() ?: DEFAULT_OFFSET
    fun save(key: String, value: String) {
        result = null
        prefs.edit { putString(key, value) }
    }
    fun saveCommute(key: String, value: String) {
        changes = emptyList()
        save(key, value)
    }
    // Where the rider hasn't set a time: the offset below the official minimum, at least 0 (user, 2026-10-06).
    fun defaultAt(stop: Stop) = maxOf(minimums.at(stop).toMinutes() - offsetMinutes, 0)
    when (screen) {
        Screen.HELP -> Help(onBack = { screen = Screen.SEARCH })
        Screen.SETTINGS -> Settings(offset, !searching, { offset = it; save("offset", it) }, onBack = { screen = Screen.SEARCH })
        Screen.SEARCH -> Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(onSettings = { screen = Screen.SETTINGS }, onHelp = { screen = Screen.HELP })
            Column(
                Modifier.verticalScroll(scroll).padding(horizontal = 16.dp).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The swap button hovers on the right, centred between From and To, without moving
                // them apart (user, 2026-10-06). Each field has its label's 8 dp above its border,
                // so the gap between the borders is 4 dp below the middle.
                Box(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field(stringResource(R.string.from), from, !searching) { from = it; saveCommute("from", it) }
                        Field(stringResource(R.string.to), to, !searching) { to = it; saveCommute("to", it) }
                    }
                    FilledTonalIconButton(
                        onClick = { val f = from; from = to; to = f; saveCommute("from", from); saveCommute("to", to) },
                        Modifier.align(Alignment.CenterEnd).offset(y = 4.dp).padding(end = 12.dp),
                        enabled = !searching,
                    ) { Text("⇅", fontSize = 20.sp) }
                }
                Field(stringResource(R.string.leaving_at), leaving, !searching, "08:50", isError = leaving.isNotEmpty() && time == null, number = true) {
                    leaving = it; saveCommute("leaving", it)
                }
                Button(
                    enabled = !searching && from.isNotBlank() && to.isNotBlank() && time != null,
                    onClick = {
                        result = Result.Searching
                        val transfer = { stop: Stop -> Duration.ofMinutes(prefs.getString(stop.id, null)?.toLongOrNull() ?: defaultAt(stop)) }
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { find(from.trim(), to.trim(), time!!, transfer) }
                            result = r
                            changes = (r as? Result.Found)?.changes.orEmpty()
                        }
                    },
                ) { Text(stringResource(R.string.search)) }
                when (val r = result) {
                    null -> {}
                    Result.Searching -> Text(stringResource(R.string.searching))
                    Result.Failed -> Text(stringResource(R.string.search_failed), color = MaterialTheme.colorScheme.error)
                    is Result.Found -> {
                        // The day in the language of the texts, not the phone's: a Spanish phone gets English.
                        val locale = Locale.forLanguageTag(stringResource(R.string.language))
                        val day = r.day.format(DateTimeFormatter.ofPattern(stringResource(R.string.day_pattern), locale))
                        Text(when {
                            r.connections == 0 -> stringResource(R.string.no_connections, day)
                            r.finds.isEmpty() -> stringResource(R.string.nothing_faster, day)
                            else -> "$day:"
                        })
                        r.finds.forEach { FindCard(it, minimums) }
                    }
                }
                changes.forEach { stop ->
                    key(stop.id) {
                        var minutes by remember { mutableStateOf(prefs.getString(stop.id, "")!!) }
                        MinutesField(
                            stringResource(R.string.switch_time_at, stop.station),
                            minutes, defaultAt(stop), minimums.at(stop).toMinutes(), !searching,
                        ) {
                            minutes = it; save(stop.id, it)
                        }
                    }
                }
            }
        }
    }
}

/** Settings (⚙) on the left, Help (?) on the right, as in gridload (user, 2026-10-06). */
@Composable
private fun TopBar(onSettings: () -> Unit, onHelp: () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
    TextButton(onClick = onSettings) { Text("⚙", fontSize = 22.sp) }
    Text("Gleiswechsel", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
    TextButton(onClick = onHelp) { Text("?", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun Field(
    label: String,
    value: String,
    enabled: Boolean,
    placeholder: String? = null,
    isError: Boolean = false,
    number: Boolean = false,
    onValueChange: (String) -> Unit,
) = OutlinedTextField(
    value, onValueChange, Modifier.fillMaxWidth(), enabled,
    label = { Text(label) },
    placeholder = placeholder?.let { { Text(it) } },
    isError = isError,
    keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
    singleLine = true,
)

/**
 * [label] and its minutes in a box, as typed (digits, up to 2), with a − in front if they're
 * [minus]. Empty shows [default] in light grey: the usual placeholder grey looks like a set value.
 * The box is the one of [MinutesBox], coloured against [official].
 */
@Composable
internal fun MinutesField(
    label: String,
    minutes: String,
    default: Long,
    official: Long,
    enabled: Boolean,
    minus: Boolean = false,
    onValueChange: (String) -> Unit,
) =
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        val box = boxColor((minutes.toLongOrNull() ?: default) * if (minus) -1 else 1, official)
        OutlinedTextField(
            minutes, { onValueChange(it.filter(Char::isDigit).take(2)) }, Modifier.width(104.dp), enabled,
            textStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold),
            placeholder = { Text("$default", color = MaterialTheme.colorScheme.outline) },
            prefix = if (minus) { { Text("−") } } else null,
            suffix = { Text("min") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = box, unfocusedContainerColor = box, disabledContainerColor = box,
            ),
        )
    }

/**
 * A track switch time in a box, the number the app is about (user, 2026-10-06), shaped as in
 * [MinutesField] and coloured against the [official] one.
 */
@Composable
private fun MinutesBox(minutes: Long, official: Long) = Surface(
    shape = MaterialTheme.shapes.extraSmall,
    color = boxColor(minutes, official),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
) {
    Text("$minutes min", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontWeight = FontWeight.Bold)
}

/** Green below the official track switch time, orange at it, red above (user, 2026-10-06). */
private fun boxColor(minutes: Long, official: Long) = when {
    minutes < official -> Color(0xFFA5D6A7)
    minutes == official -> Color(0xFFFFCC80)
    else -> Color(0xFFEF9A9A)
}

/** `8:50`, `08:50`, `850` or `0850`; null if it isn't a time. */
private fun parseTime(text: String): LocalTime? {
    val digits = text.filter { it.isDigit() }.takeIf { it.length in 3..4 }?.padStart(4, '0') ?: return null
    return runCatching { LocalTime.of(digits.take(2).toInt(), digits.drop(2).toInt()) }.getOrNull()
}

/** [from] → [to] at the next [leaving], Swiss time, searched live with the rider's [transfer] times. Blocking. */
private fun find(from: String, to: String, leaving: LocalTime, transfer: (Stop) -> Duration): Result {
    val now = LocalDateTime.now(ZoneId.of("Europe/Zurich"))
    val time = now.toLocalDate().atTime(leaving).let { if (it.isBefore(now)) it.plusDays(1) else it }
    return try {
        val ask = { a: String, b: String, at: LocalDateTime -> connections(a, b, at, BuildConfig.VERSION_NAME) }
        val officials = ask(from, to, time)
        Result.Found(time, officials.size, officials.flatMap { it.changes }.distinctBy { it.id }, search(officials, transfer, ask))
    } catch (e: Exception) {
        // In the message too: Log drops the stack trace of an UnknownHostException (no network).
        Log.w("Gleiswechsel", "Search failed: $e", e)
        Result.Failed
    }
}

@Composable
private fun FindCard(find: Find, minimums: Minimums) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.earlier, find.saved.toMinutes()),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(
                    R.string.instead_of,
                    find.faster.arrival.station,
                    find.faster.arrival.time.format(hourMinute),
                    find.official.arrival.time.format(hourMinute),
                ),
            )
            Text(
                stringResource(R.string.more_efficient, (find.moreEfficient * 100).roundToInt()),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            Trip(find.faster, minimums)
        }
    }
}

/**
 * [trip] as a timetable: a row per stop, the train in between, and at each change its minutes in a
 * box, coloured against the station's [minimums]. A walk between two trains is part of the change;
 * one before the first train or after the last is shown on its own.
 */
@Composable
private fun Trip(trip: Connection, minimums: Minimums) {
    val legs = trip.legs
    val rides = legs.indices.filter { legs[it].train != null }
    legs.forEachIndexed { i, leg ->
        val train = leg.train
        val next = rides.firstOrNull { it > i }
        when {
            train != null -> {
                StopRow(leg.departure)
                Indented { Text(train, fontWeight = FontWeight.Bold) }
                StopRow(leg.arrival)
                if (next != null) Indented {
                    MinutesBox(
                        Duration.between(leg.arrival.time, legs[next].departure.time).toMinutes(),
                        minimums.at(leg.arrival).toMinutes(),
                    )
                    val walk = legs.subList(i + 1, next).sumOf { it.minutes }
                    Text(
                        if (walk > 0) stringResource(R.string.track_switch_walk, walk) else stringResource(R.string.track_switch),
                        Modifier.padding(start = 4.dp),
                    )
                }
            }
            i < (rides.firstOrNull() ?: 0) -> { StopRow(leg.departure); Indented { Text(stringResource(R.string.walk, leg.minutes)) } }
            next == null -> { Indented { Text(stringResource(R.string.walk, leg.minutes)) }; StopRow(leg.arrival) }
        }
    }
}

@Composable
private fun StopRow(stop: Stop) = Row {
    Text(stop.time.format(hourMinute), Modifier.width(TIME_COLUMN))
    Text(stop.station, Modifier.weight(1f))
    stop.platform?.let { Text(stringResource(R.string.track, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** Under a stop's name, past the times: the train, a walk, a change. */
@Composable
private fun Indented(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.padding(start = TIME_COLUMN), verticalAlignment = Alignment.CenterVertically, content = content)

private val TIME_COLUMN = 56.dp

private val Leg.minutes get() = Duration.between(departure.time, arrival.time).toMinutes()

private val hourMinute = DateTimeFormatter.ofPattern("HH:mm")
