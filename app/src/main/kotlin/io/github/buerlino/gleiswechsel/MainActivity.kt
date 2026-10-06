package io.github.buerlino.gleiswechsel

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import io.github.buerlino.gleiswechsel.core.Connection
import io.github.buerlino.gleiswechsel.core.Find
import io.github.buerlino.gleiswechsel.core.Found
import io.github.buerlino.gleiswechsel.core.Leg
import io.github.buerlino.gleiswechsel.core.Minimums
import io.github.buerlino.gleiswechsel.core.Stop
import io.github.buerlino.gleiswechsel.core.connections
import io.github.buerlino.gleiswechsel.core.found
import io.github.buerlino.gleiswechsel.core.search
import io.github.buerlino.gleiswechsel.core.shortestChanges
import io.github.buerlino.gleiswechsel.core.ticketUrl
import io.github.buerlino.gleiswechsel.core.tooShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    class Done(val found: Found) : Result
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
        // The last result (user, 2026-10-06); one that can't be read, e.g. an older app's, is ignored.
        val saved = File(filesDir, "result.json")
        val last = try {
            saved.takeIf { it.exists() }?.let { found(it.readText()) }
        } catch (e: Exception) {
            Log.w("Gleiswechsel", "Last result not read: $e", e)
            null
        }
        setContent {
            MaterialTheme(lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App(prefs, minimums, saved, last) }
            }
        }
    }
}

/**
 * The search page, or Help or Settings in its place. Every field is saved in [prefs] as it is set:
 * the commute, the rider's transfer time at each change station (keyed by station id), the offset
 * and whether Optimization is on. Editing one clears the result; a time, the offset or Optimization
 * keeps the change stations. A result with connections is kept in [saved] until it's cleared, so
 * the page opens with it again ([last]).
 *
 * The page has three panels (user, 2026-10-06): Destination (the commute), Journey (the result)
 * and Optimization (the rider's time at each change station).
 */
@Composable
private fun App(prefs: SharedPreferences, minimums: Minimums, saved: File, last: Found?) {
    var screen by rememberSaveable { mutableStateOf(Screen.SEARCH) }
    var from by remember { mutableStateOf(prefs.getString("from", "")!!) }
    var to by remember { mutableStateOf(prefs.getString("to", "")!!) }
    var leaving by remember { mutableStateOf(prefs.getString("leaving", "")!!) }
    var offset by remember { mutableStateOf(prefs.getString("offset", "")!!) }
    var optimize by remember { mutableStateOf(prefs.getBoolean("optimize", true)) }
    var result by remember { mutableStateOf<Result?>(last?.let { Result.Done(it) }) }
    var changes by remember { mutableStateOf(last?.changes.orEmpty()) }
    var official by remember { mutableStateOf(last?.let { minimums.lowered(it.shortest) } ?: minimums) }
    var job by remember { mutableStateOf<Job?>(null) }
    var open by remember { mutableStateOf(last == null) }
    var optimizationOpen by remember { mutableStateOf(true) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val time = parseTime(leaving)
    val searching = result == Result.Searching
    val offsetMinutes = offset.toLongOrNull() ?: DEFAULT_OFFSET
    fun show(r: Result?) {
        result = r
        val found = (r as? Result.Done)?.found?.takeIf { it.first != null }
        try {
            if (found != null) saved.writeText(found.toJson()) else saved.delete()
        } catch (e: Exception) {
            Log.w("Gleiswechsel", "Result not saved: $e", e)
        }
    }
    fun save(key: String, value: String) {
        show(null)
        prefs.edit { putString(key, value) }
    }
    fun saveCommute(key: String, value: String) {
        changes = emptyList()
        save(key, value)
    }
    // Where the rider hasn't set a time: the offset below the official minimum, at least 0 (user, 2026-10-06).
    fun defaultAt(stop: Stop, official: Minimums) = maxOf(official.at(stop).toMinutes() - offsetMinutes, 0)
    // With Optimization off, the times set at stations are kept but not used.
    fun riderAt(stop: Stop, official: Minimums) =
        prefs.getString(stop.id, null)?.toLongOrNull()?.takeIf { optimize } ?: defaultAt(stop, official)
    when (screen) {
        Screen.HELP -> Help(onBack = { screen = Screen.SEARCH })
        Screen.SETTINGS -> Settings(
            offset, optimize, !searching,
            onOffset = { offset = it; save("offset", it) },
            onOptimize = { optimize = it; show(null); prefs.edit { putBoolean("optimize", it) } },
            onBack = { screen = Screen.SEARCH },
        )
        Screen.SEARCH -> Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(onSettings = { screen = Screen.SETTINGS }, onHelp = { screen = Screen.HELP })
            // Before a search Destination is in the middle of the page; a search moves it to the top,
            // folded to one line, and a tap on that opens it again (user, 2026-10-06).
            val centred = result == null && changes.isEmpty()
            BoxWithConstraints {
                var formHeight by remember { mutableIntStateOf(0) }
                val free = maxHeight - with(LocalDensity.current) { formHeight.toDp() }
                val top by animateDpAsState(if (centred) (free / 2).coerceAtLeast(0.dp) else 0.dp, label = "top")
                Column(
                    Modifier.verticalScroll(scroll).padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        Modifier.padding(top = top).onSizeChanged { formHeight = it.height },
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Heading(stringResource(R.string.destination))
                        AnimatedVisibility(open || centred) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // The swap button hovers on the right, centred between From and To,
                                // without moving them apart (user, 2026-10-06). Each field has its
                                // label's 8 dp above its border, so the gap between the borders is
                                // 4 dp below the middle.
                                Box(Modifier.fillMaxWidth()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Field(stringResource(R.string.from), from, !searching) { from = it; saveCommute("from", it) }
                                        Field(stringResource(R.string.to), to, !searching) { to = it; saveCommute("to", it) }
                                    }
                                    FilledTonalIconButton(
                                        onClick = { val f = from; from = to; to = f; saveCommute("from", from); saveCommute("to", to) },
                                        Modifier.align(Alignment.CenterEnd).offset(y = 4.dp).padding(end = 12.dp),
                                        enabled = !searching,
                                    ) { Text("⇅", Modifier.spokenAs(stringResource(R.string.swap)), fontSize = 20.sp) }
                                }
                                Field(stringResource(R.string.leaving_at), leaving, !searching, "08:50", isError = leaving.isNotEmpty() && time == null, number = true) {
                                    leaving = it; saveCommute("leaving", it)
                                }
                            }
                        }
                        AnimatedVisibility(!open && !centred) {
                            Folded("${from.trim()} → ${to.trim()}, ${time?.format(hourMinute) ?: leaving}") { open = true }
                        }
                        // While it searches, Search is Cancel (user, 2026-10-06): the requests can't be
                        // stopped, so their late answer is dropped. Opened after a search, the fields fold
                        // again with ▴ on its right (user, 2026-10-06; one mark each way, none on the title).
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                enabled = searching || from.isNotBlank() && to.isNotBlank() && time != null,
                                onClick = {
                                    if (searching) {
                                        job?.cancel()
                                        show(null)
                                        return@Button
                                    }
                                    open = false
                                    show(Result.Searching)
                                    val transfer = { stop: Stop, official: Minimums -> Duration.ofMinutes(riderAt(stop, official)) }
                                    job = scope.launch {
                                        val r = withContext(Dispatchers.IO) { find(from.trim(), to.trim(), time!!, minimums, transfer) }
                                        show(r)
                                        val found = (r as? Result.Done)?.found
                                        found?.let { official = minimums.lowered(it.shortest) }
                                        changes = found?.changes.orEmpty()
                                    }
                                },
                            ) { Text(stringResource(if (searching) R.string.cancel else R.string.search)) }
                            Spacer(Modifier.weight(1f))
                            if (open && !centred) TextButton({ open = false }) {
                                Text("▴", Modifier.spokenAs(stringResource(R.string.hide_trip)), fontSize = 22.sp)
                            }
                        }
                    }
                    // ✕ closes the result and the rows, so Destination is back in the middle, as on start
                    // (user, 2026-10-06); while a search runs, Search is Cancel instead.
                    if (result != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { Heading(stringResource(R.string.journey)) }
                        if (!searching) TextButton({ show(null); changes = emptyList() }, Modifier.padding(top = 8.dp)) {
                            Text("✕", Modifier.spokenAs(stringResource(R.string.close_journey)), fontSize = 20.sp)
                        }
                    }
                    when (val r = result) {
                        null -> {}
                        Result.Searching -> Text(stringResource(R.string.searching))
                        Result.Failed -> Text(stringResource(R.string.search_failed), color = MaterialTheme.colorScheme.error)
                        is Result.Done -> {
                            val f = r.found
                            val first = f.first
                            // The day in the language of the texts, not the phone's: a Spanish phone gets English.
                            val locale = Locale.forLanguageTag(stringResource(R.string.language))
                            val day = f.day.format(DateTimeFormatter.ofPattern(stringResource(R.string.day_pattern), locale))
                            Text(when {
                                first == null -> stringResource(R.string.no_connections, day)
                                f.finds.isEmpty() -> stringResource(R.string.nothing_faster, day)
                                else -> "$day:"
                            })
                            // Delays go stale: when they were read (user, 2026-10-06).
                            if (f.delaysKnown) Text(
                                stringResource(R.string.delays_as_of, f.asOf.format(hourMinute)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (f.incomplete) Text(stringResource(R.string.not_all_checked), color = MaterialTheme.colorScheme.error)
                            val rider = { stop: Stop -> riderAt(stop, official) }
                            f.finds.forEach { FindCard(it, official, rider) }
                            // Nothing faster: the official connection leaving first, to see where it changes (user, 2026-10-06).
                            if (f.finds.isEmpty() && first != null) Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.official_connection), style = MaterialTheme.typography.titleMedium)
                                    Trip(first, official, rider)
                                }
                            }
                        }
                    }
                    if (optimize && changes.isNotEmpty()) {
                        Heading(stringResource(R.string.optimization), optimizationOpen) { optimizationOpen = !optimizationOpen }
                        AnimatedVisibility(optimizationOpen) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.optimization_text), style = MaterialTheme.typography.bodySmall)
                                changes.forEach { stop ->
                                    key(stop.id) {
                                        var minutes by remember { mutableStateOf(prefs.getString(stop.id, null)?.toLongOrNull()) }
                                        MinutesStepper(
                                            stop.station, minutes, defaultAt(stop, official), official.at(stop).toMinutes(), !searching, station = true,
                                        ) {
                                            minutes = it; save(stop.id, it?.toString() ?: "")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A panel's title (user, 2026-10-06); with [open] given, a tap folds or opens the panel and a
 * ▾ or ▸ says which.
 */
@Composable
internal fun Heading(text: String, open: Boolean? = null, onClick: () -> Unit = {}) = Row(
    Modifier.fillMaxWidth().padding(top = 8.dp).then(if (open != null) Modifier.folding(open, onClick) else Modifier),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (open != null) FoldMark(open)
}

/** Folds or opens something on a tap; a screen reader says which it is (the ▾ or ▸ is hidden from it). */
@Composable
private fun Modifier.folding(open: Boolean, onClick: () -> Unit, label: String? = null): Modifier {
    val state = stringResource(if (open) R.string.state_open else R.string.state_folded)
    return clickable(onClickLabel = label, onClick = onClick).semantics { stateDescription = state }
}

@Composable
private fun FoldMark(open: Boolean) = Text(if (open) "▾" else "▸", Modifier.clearAndSetSemantics {})

/** The trip in one line, [text], after a search; a tap opens the fields again. */
@Composable
private fun Folded(text: String, onOpen: () -> Unit) = Surface(
    Modifier.fillMaxWidth().folding(false, onOpen, stringResource(R.string.change_trip)),
    shape = MaterialTheme.shapes.extraSmall,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f))
        Text("▾", Modifier.clearAndSetSemantics {})
    }
}

/**
 * Settings (⚙) on the left, Help (?) on the right, as in gridload (user, 2026-10-06). A tap on the
 * title picks the language, a hidden extra (user, 2026-10-06), on Android 13 and later, which keep
 * an app's own language.
 */
@Composable
private fun TopBar(onSettings: () -> Unit, onHelp: () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
    TextButton(onClick = onSettings) { Text("⚙", Modifier.spokenAs(stringResource(R.string.settings)), fontSize = 22.sp) }
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        var menu by remember { mutableStateOf(false) }
        val context = LocalContext.current
        Text(
            stringResource(R.string.title),
            if (Build.VERSION.SDK_INT >= 33) Modifier.clickable { menu = true } else Modifier,
            style = MaterialTheme.typography.titleLarge,
        )
        if (Build.VERSION.SDK_INT >= 33) DropdownMenu(menu, { menu = false }) {
            languages.forEach { (tag, name) ->
                DropdownMenuItem({ Text(name ?: stringResource(R.string.phone_language)) }, {
                    menu = false
                    context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
                })
            }
        }
    }
    TextButton(onClick = onHelp) { Text("?", Modifier.spokenAs(stringResource(R.string.help)), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
}

/** Each language in itself; an empty tag (no name) goes back to the phone's. */
private val languages = listOf(
    "en" to "English", "de-CH" to "Deutsch", "fr-CH" to "Français", "it-CH" to "Italiano", "" to null,
)

/** A screen reader says [words] instead of the symbol (it would read out "⇅"). */
internal fun Modifier.spokenAs(words: String) = clearAndSetSemantics { contentDescription = words }

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
 * A track switch time in a box, the number the app is about (user, 2026-10-06), coloured against
 * the [official] one, without a border (user, 2026-10-06); [faded] when it's a default. With
 * [arrows], an ↓ below the official one and an ↑ above, and a screen reader says which (user,
 * 2026-10-06: the colour alone is lost on colour-blind riders); the offset has its sign instead.
 */
@Composable
private fun MinutesBox(minutes: Long, official: Long, faded: Boolean = false, minWidth: Dp = 0.dp, arrows: Boolean = true) =
    boxColors(minutes, official).let { (box, ink) ->
        val words = when {
            !arrows -> null
            minutes < official -> stringResource(R.string.box_below, minutes, official)
            minutes == official -> stringResource(R.string.box_same, minutes)
            else -> stringResource(R.string.box_above, minutes, official)
        }
        val arrow = if (!arrows || minutes == official) "" else if (minutes < official) "↓ " else "↑ "
        Surface(shape = MaterialTheme.shapes.extraSmall, color = box) {
            Text(
                "$arrow$minutes min".replace('-', '−'),
                Modifier.widthIn(min = minWidth).padding(horizontal = 8.dp, vertical = 2.dp)
                    .then(if (words != null) Modifier.spokenAs(words) else Modifier),
                color = if (faded) ink.copy(alpha = FADED) else ink,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }

/**
 * [label] and its track switch time with − and + (user, 2026-10-06: a box to type in didn't look
 * changeable), within [range]. [minutes] as saved, null for the [default]; stepping onto the
 * default unsets it again, so it follows the offset. The label is underlined if it's a [station].
 */
@Composable
internal fun MinutesStepper(
    label: String,
    minutes: Long?,
    default: Long,
    official: Long,
    enabled: Boolean,
    station: Boolean = false,
    range: LongRange = 0L..99,
    arrows: Boolean = true,
    onChange: (Long?) -> Unit,
) = Row(verticalAlignment = Alignment.CenterVertically) {
    val value = minutes ?: default
    fun step(by: Long) = (value + by).let { onChange(it.takeIf { it != default }) }
    Text(label, Modifier.weight(1f), textDecoration = if (station) TextDecoration.Underline else null)
    IconButton({ step(-1) }, enabled = enabled && value > range.first) {
        Text("−", Modifier.spokenAs(stringResource(R.string.less)), fontSize = 20.sp)
    }
    MinutesBox(value, official, faded = minutes == null, minWidth = 88.dp, arrows = arrows)
    IconButton({ step(1) }, enabled = enabled && value < range.last) {
        Text("+", Modifier.spokenAs(stringResource(R.string.more)), fontSize = 20.sp)
    }
}

/**
 * A change the delays make shorter than the rider's [needed] minutes (user, 2026-10-06): red with
 * a "!" and the [minutes] left; grey and without it once they're below zero, the next train gone.
 */
@Composable
private fun LateBox(minutes: Long, needed: Long) {
    val missed = minutes < 0
    Surface(shape = MaterialTheme.shapes.extraSmall, color = if (missed) Color(0xFF757575) else RED) {
        Text(
            (if (missed) "$minutes min" else "! $minutes min").replace('-', '−'),
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp).spokenAs(
                if (missed) stringResource(R.string.box_missed, -minutes) else stringResource(R.string.box_too_short, minutes, needed),
            ),
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

private val RED = Color(0xFFC62828)

/** A default track switch time's text, not set by the rider. */
private const val FADED = 0.6f

/**
 * The box and its text: green below the official track switch time, orange at it, red above
 * (user, 2026-10-06), gridload's three with its text colours.
 */
private fun boxColors(minutes: Long, official: Long) = when {
    minutes < official -> Color(0xFF2E7D32) to Color.White
    minutes == official -> Color(0xFFFFA000) to Color.Black
    else -> RED to Color.White
}

/** `8:50`, `08:50`, `850` or `0850`; null if it isn't a time. */
private fun parseTime(text: String): LocalTime? {
    val digits = text.filter { it.isDigit() }.takeIf { it.length in 3..4 }?.padStart(4, '0') ?: return null
    return runCatching { LocalTime.of(digits.take(2).toInt(), digits.drop(2).toInt()) }.getOrNull()
}

/**
 * [from] → [to] at the next [leaving], Swiss time, searched live with the rider's [transfer] times,
 * given the official [minimums] lowered by the planner's answers so far. The changes are the
 * stations the search changed at, in the order it did. Only the official connections' request
 * failing fails the search; a failed onward request skips its change. Blocking.
 */
private fun find(
    from: String,
    to: String,
    leaving: LocalTime,
    minimums: Minimums,
    transfer: (Stop, Minimums) -> Duration,
): Result {
    val now = LocalDateTime.now(ZoneId.of("Europe/Zurich"))
    val time = now.toLocalDate().atTime(leaving).let { if (it.isBefore(now)) it.plusDays(1) else it }
    return try {
        val answers = mutableListOf<Connection>()
        val ask = { a: String, b: String, at: LocalDateTime -> connections(a, b, at, BuildConfig.VERSION_NAME).also { answers += it } }
        val officials = ask(from, to, time)
        val changes = mutableListOf<Stop>()
        val searched = search(officials, { stop -> changes += stop; transfer(stop, minimums.lowered(shortestChanges(answers))) }, ask)
        searched.failures.forEach { Log.w("Gleiswechsel", "Change not checked: $it", it) }
        Result.Done(
            Found(time, officials.firstOrNull(), changes.distinctBy { it.id }, searched.finds, shortestChanges(answers), searched.failures.isNotEmpty(), now),
        )
    } catch (e: Exception) {
        // In the message too: Log drops the stack trace of an UnknownHostException (no network).
        Log.w("Gleiswechsel", "Search failed: $e", e)
        Result.Failed
    }
}

@Composable
private fun FindCard(find: Find, minimums: Minimums, rider: (Stop) -> Long) {
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
            // It may need another ticket (user, 2026-10-06).
            if (find.faster.doublesBack) Text(
                stringResource(R.string.doubles_back),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            Trip(find.faster, minimums, rider)
            // The official connection, folded and quiet (user, 2026-10-06); on the same line the
            // ticket on sbb.ch, for the official connection's from, to and departure (user, 2026-10-06).
            var official by remember { mutableStateOf(false) }
            val uri = LocalUriHandler.current
            val context = LocalContext.current
            val ticket = ticketUrl(find.official.departure, find.official.arrival, stringResource(R.string.language))
            val noBrowser = stringResource(R.string.no_browser)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.folding(official, { official = !official }), verticalAlignment = Alignment.CenterVertically) {
                    ProvideTextStyle(MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        Text(stringResource(R.string.official_connection) + " ")
                        FoldMark(official)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton({
                    // A phone without a browser has nothing to open it with.
                    try {
                        uri.openUri(ticket)
                    } catch (e: Exception) {
                        Log.w("Gleiswechsel", "No browser: $e", e)
                        Toast.makeText(context, noBrowser, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.ticket)) }
            }
            AnimatedVisibility(official) {
                Column(Modifier.alpha(FADED), verticalArrangement = Arrangement.spacedBy(4.dp)) { Trip(find.official, minimums, rider) }
            }
        }
    }
}

/**
 * [trip] as a timetable: a row per stop, the train in between, and at each change its minutes in a
 * box, coloured against the station's [minimums], or a [LateBox] where the delays make it shorter than
 * the [rider]'s time there. A walk between two trains is part of the change; one before the first
 * train or after the last is shown on its own.
 */
@Composable
private fun Trip(trip: Connection, minimums: Minimums, rider: (Stop) -> Long) {
    val legs = trip.legs
    val rides = legs.indices.filter { legs[it].train != null }
    legs.forEachIndexed { i, leg ->
        val train = leg.train
        val next = rides.firstOrNull { it > i }
        when {
            train != null -> {
                StopRow(leg.departure)
                Indented {
                    Text(
                        train,
                        Modifier.border(1.dp, Color.Black, MaterialTheme.shapes.extraSmall).padding(horizontal = 6.dp),
                        fontWeight = FontWeight.Bold,
                    )
                }
                StopRow(leg.arrival)
                if (next != null) Indented {
                    val needed = rider(leg.arrival)
                    val late = tooShort(leg.arrival, legs[next].departure, Duration.ofMinutes(needed))
                    if (late != null) LateBox(late.toMinutes(), needed) else MinutesBox(
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

/**
 * A stop: the planned time and a delay of a minute or more in red after it, the station, the track.
 * A changed track (user, 2026-10-06): the new one on the sign, the planned one struck through.
 */
@Composable
private fun StopRow(stop: Stop) = Row(verticalAlignment = Alignment.CenterVertically) {
    Row(Modifier.widthIn(min = TIME_COLUMN), verticalAlignment = Alignment.CenterVertically) {
        Text(stop.time.format(hourMinute))
        stop.delay?.takeIf { it > 0 }?.let {
            Text(
                "+$it",
                Modifier.padding(horizontal = 4.dp).spokenAs(stringResource(R.string.late, it)),
                color = RED,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    Text(stop.station, Modifier.weight(1f), textDecoration = TextDecoration.Underline)
    val track = stop.newPlatform ?: stop.platform ?: return@Row
    val words = stop.newPlatform?.let { new -> stop.platform?.let { stringResource(R.string.track_changed, new, it) } }
    Row(if (words != null) Modifier.spokenAs(words) else Modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.track), Modifier.padding(end = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (stop.newPlatform != null) stop.platform?.let {
            Text(
                it,
                Modifier.padding(end = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TrackSign(track)
    }
}

/**
 * A track's number as on a platform sign: white on blue (the logo's), square corners, a rounded
 * white line inside the edge (user, 2026-10-06).
 */
@Composable
private fun TrackSign(track: String) = Surface(color = Color(0xFF00179B)) {
    Text(
        track,
        Modifier.padding(2.dp).border(1.dp, Color.White, MaterialTheme.shapes.extraSmall).padding(horizontal = 5.dp),
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
    )
}

/** Under a stop's name, past the times: the train, a walk, a change. */
@Composable
private fun Indented(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.padding(start = TIME_COLUMN), verticalAlignment = Alignment.CenterVertically, content = content)

private val TIME_COLUMN = 56.dp

private val Leg.minutes get() = Duration.between(departure.time, arrival.time).toMinutes()

private val hourMinute = DateTimeFormatter.ofPattern("HH:mm")
