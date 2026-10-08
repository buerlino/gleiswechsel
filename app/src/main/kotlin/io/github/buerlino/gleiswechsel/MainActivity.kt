package io.github.buerlino.gleiswechsel

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import io.github.buerlino.gleiswechsel.core.Found
import io.github.buerlino.gleiswechsel.core.Minimums
import io.github.buerlino.gleiswechsel.core.TIMETABLE_URL
import io.github.buerlino.gleiswechsel.core.TrackSwitchTimes
import io.github.buerlino.gleiswechsel.core.connections
import io.github.buerlino.gleiswechsel.core.download
import io.github.buerlino.gleiswechsel.core.fastestOfTheDay
import io.github.buerlino.gleiswechsel.core.find
import io.github.buerlino.gleiswechsel.core.found
import io.github.buerlino.gleiswechsel.core.localTimetable
import io.github.buerlino.gleiswechsel.core.parseTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
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
            // The buttons, the switch and the links in the logo's blue (user, 2026-10-07), not Material's purple.
            MaterialTheme(lightColorScheme(primary = BLUE)) {
                // The timetable's copy in the cache (user, 2026-10-07): it can be downloaded again.
                Surface(Modifier.fillMaxSize()) { App(prefs, minimums, saved, last, File(cacheDir, "timetable.bin.gz")) }
            }
        }
    }
}

/**
 * The search page, or Help or Settings in its place. Every field is saved in [prefs] as it is set:
 * the commute, the rider's transfer time at each change station (keyed by station id), the offset
 * and whether Optimization is on. Editing one clears the result; a time, the offset or Optimization
 * keeps the change stations. A result with connections is kept in [saved] until it's cleared, so
 * the page opens with it again ([last]). The full search reads the timetable's [copy].
 *
 * The page has three panels (user, 2026-10-06): Destination (the commute), Journey (the result)
 * and Optimization (the rider's time at each change station).
 */
@Composable
private fun App(prefs: SharedPreferences, minimums: Minimums, saved: File, last: Found?, copy: File) {
    var screen by rememberSaveable { mutableStateOf(Screen.SEARCH) }
    var from by remember { mutableStateOf(prefs.getString("from", "")!!) }
    var to by remember { mutableStateOf(prefs.getString("to", "")!!) }
    var leaving by remember { mutableStateOf(prefs.getString("leaving", "")!!) }
    var allDay by remember { mutableStateOf(prefs.getBoolean("allDay", false)) }
    var offset by remember { mutableStateOf(prefs.getString("offset", "")!!) }
    var optimize by remember { mutableStateOf(prefs.getBoolean("optimize", true)) }
    var result by remember { mutableStateOf<Result?>(last?.let { Result.Done(it) }) }
    var stale by remember { mutableStateOf(false) }
    var changes by remember { mutableStateOf(last?.changes.orEmpty()) }
    var job by remember { mutableStateOf<Job?>(null) }
    var open by remember { mutableStateOf(last == null) }
    var optimizationOpen by remember { mutableStateOf(true) }
    val scroll = rememberScrollState()
    val searchButton = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val time = parseTime(leaving)
    val searching = result == Result.Searching
    val offsetMinutes = offset.toLongOrNull() ?: DEFAULT_OFFSET
    fun show(r: Result?) {
        result = r
        stale = false
        val found = (r as? Result.Done)?.found?.takeIf { it.first != null }
        try {
            if (found != null) saved.writeText(found.toJson()) else saved.delete()
        } catch (e: Exception) {
            Log.w("Gleiswechsel", "Result not saved: $e", e)
        }
    }
    // A time, the offset or Optimization changed: the finds stay, faded, until the next Search, so the
    // rows don't move under the finger (user, 2026-10-07); the saved file goes.
    fun fade() {
        val r = result
        show(null)
        if (r is Result.Done) { result = r; stale = true }
    }
    fun save(key: String, value: String) {
        fade()
        prefs.edit { putString(key, value) }
    }
    fun saveCommute(key: String, value: String) {
        show(null)
        changes = emptyList()
        prefs.edit { putString(key, value) }
    }
    // With Optimization off, the times set at stations are kept but not used.
    val times = TrackSwitchTimes(minimums, offsetMinutes) { id -> prefs.getString(id, null)?.toLongOrNull()?.takeIf { optimize } }
    // An empty time is now (user, 2026-10-07); with all day on, the time is ignored.
    val ready = from.isNotBlank() && to.isNotBlank() && (allDay || leaving.isBlank() || time != null)
    fun startSearch() {
        open = false
        show(Result.Searching)
        job = scope.launch {
            val r = withContext(Dispatchers.IO) { search(from.trim(), to.trim(), time, allDay, times, copy) }
            show(r)
            changes = (r as? Result.Done)?.found?.changes.orEmpty()
        }
    }
    when (screen) {
        Screen.HELP -> Help(onBack = { screen = Screen.SEARCH })
        Screen.SETTINGS -> Settings(
            offset, optimize, !searching,
            onOffset = { offset = it; save("offset", it) },
            onOptimize = { optimize = it; fade(); prefs.edit { putBoolean("optimize", it) } },
            onBack = { screen = Screen.SEARCH },
        )
        Screen.SEARCH -> Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(onSettings = { screen = Screen.SETTINGS }, onHelp = { screen = Screen.HELP })
            // Before a search Destination is in the middle of the page; a search moves it to the top,
            // folded to one line, and a tap on that opens it again (user, 2026-10-06).
            val centred = result == null && (changes.isEmpty() || !optimize)
            BoxWithConstraints {
                val space = constraints.maxHeight
                val middle by animateFloatAsState(if (centred) 0.5f else 0f, label = "middle")
                Column(
                    Modifier.verticalScroll(scroll).padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        // Placed in the frame it's measured in, so it doesn't slide in from too low.
                        Modifier.layout { measurable, constraints ->
                            val form = measurable.measure(constraints)
                            val top = ((space - form.height) * middle).roundToInt().coerceAtLeast(0)
                            layout(form.width, top + form.height) { form.place(0, top) }
                        },
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Heading(stringResource(R.string.destination))
                        AnimatedVisibility(open || centred) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val focus = LocalFocusManager.current
                                // Focus then goes to Search, Cancel while it runs: on a hardware keyboard,
                                // clearing it alone gave it to ⚙ (2026-10-07). In touch mode a button
                                // takes no focus, so nothing changes there.
                                val onSearch = { focus.clearFocus(); if (ready) { startSearch(); searchButton.requestFocus() } }
                                // The swap button hovers on the right, centred between From and To,
                                // without moving them apart (user, 2026-10-06). Each field has its
                                // label's 8 dp above its border, so the gap between the borders is
                                // 4 dp below the middle; the ⇅ sits 3 dp low in its line (seen on
                                // the phone, 2026-10-07), so 1 dp.
                                Box(Modifier.fillMaxWidth()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Field(stringResource(R.string.from), from, !searching) { from = it; saveCommute("from", it) }
                                        Field(stringResource(R.string.to), to, !searching, onSearch = onSearch.takeIf { allDay }) {
                                            to = it; saveCommute("to", it)
                                        }
                                    }
                                    // A blue ⇅ in a grey circle, white inside, over the fields' borders (user,
                                    // 2026-10-07: easier to see; before, plain, as ⚙ and ?). Not in the focus
                                    // order: a hardware Enter in To went to it, and the next one swapped the
                                    // fields (2026-10-07); the keyboard's Next skipped it.
                                    IconButton(
                                        onClick = { val f = from; from = to; to = f; saveCommute("from", from); saveCommute("to", to) },
                                        Modifier.align(Alignment.CenterEnd).offset(y = 1.dp).padding(end = 12.dp)
                                            .focusProperties { canFocus = false }
                                            .background(MaterialTheme.colorScheme.background, CircleShape)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                        enabled = !searching,
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                    ) { Text("⇅", Modifier.spokenAs(stringResource(R.string.swap)), fontSize = 20.sp) }
                                }
                                // The fastest of the day (CLAUDE.md): "all day" on the time's right, blue when
                                // on; the time is then greyed and ignored, its text kept. Not in the focus
                                // order, as ⇅: To's key searches instead. A switch clears the focus: To's
                                // key changes with it, which brought the keyboard back up (2026-10-08).
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) {
                                        Field(
                                            stringResource(R.string.leaving_at), leaving, !searching && !allDay, stringResource(R.string.now),
                                            isError = !allDay && leaving.isNotBlank() && time == null, number = true, onSearch = onSearch,
                                        ) {
                                            leaving = it; saveCommute("leaving", it)
                                        }
                                    }
                                    TextButton(
                                        {
                                            focus.clearFocus()
                                            allDay = !allDay
                                            show(null)
                                            changes = emptyList()
                                            prefs.edit { putBoolean("allDay", allDay) }
                                        },
                                        Modifier.padding(start = 8.dp).focusProperties { canFocus = false }.semantics { selected = allDay },
                                        enabled = !searching,
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = if (allDay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    ) { Text(stringResource(R.string.all_day)) }
                                }
                            }
                        }
                        AnimatedVisibility(!open && !centred) {
                            val at = when {
                                allDay -> stringResource(R.string.all_day)
                                leaving.isBlank() -> stringResource(R.string.now)
                                else -> time?.format(hourMinute) ?: leaving
                            }
                            Folded("${from.trim()} → ${to.trim()}, $at") { open = true }
                        }
                        // While it searches, Search is Cancel (user, 2026-10-06): the requests can't be
                        // stopped, so their late answer is dropped. Opened after a search, the fields fold
                        // again with ▴ on its right (user, 2026-10-06; one mark each way, none on the title).
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                modifier = Modifier.focusRequester(searchButton),
                                enabled = searching || ready,
                                onClick = {
                                    if (searching) {
                                        job?.cancel()
                                        show(null)
                                    } else startSearch()
                                },
                            ) { Text(stringResource(if (searching) R.string.cancel else R.string.search)) }
                            Spacer(Modifier.weight(1f))
                            if (open && !centred) TextButton({ open = false }) {
                                Text("▴", Modifier.spokenAs(stringResource(R.string.hide_trip)), fontSize = 22.sp)
                            }
                        }
                    }
                    // ✕ closes the result and the rows, so Destination is back in the middle, as on start
                    // (user, 2026-10-06); while a search runs, Search is Cancel instead. Plain text, as the
                    // fold marks: a button made Journey's title taller than the others.
                    if (result != null) Row {
                        Box(Modifier.weight(1f)) { Heading(stringResource(R.string.journey)) }
                        if (!searching) Text(
                            "✕",
                            Modifier.padding(top = 8.dp).clickable { show(null); changes = emptyList() }
                                .spokenAs(stringResource(R.string.close_journey)),
                            fontSize = 20.sp,
                        )
                    }
                    when (val r = result) {
                        null -> {}
                        Result.Searching -> Text(stringResource(R.string.searching))
                        Result.Failed -> Text(stringResource(R.string.search_failed), color = MaterialTheme.colorScheme.error)
                        is Result.Done -> Column(Modifier.alpha(if (stale) FADED else 1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val f = r.found
                            val first = f.first
                            // The day in the language of the texts, not the phone's: a Spanish phone gets English.
                            val locale = Locale.forLanguageTag(stringResource(R.string.language))
                            val day = f.firstDay.format(DateTimeFormatter.ofPattern(stringResource(R.string.day_pattern), locale))
                            // The result matches allDay: switching it clears the result. Without the file or
                            // its stations the fastest of the day searched nothing, so no "nothing faster".
                            val notSearched = allDay && (f.noTimetable || f.notInTimetable)
                            val nothing = f.finds.isEmpty() && f.fastest.isEmpty() && !notSearched
                            Text(when {
                                first == null -> stringResource(R.string.no_connections, day)
                                nothing -> stringResource(R.string.nothing_faster, day)
                                else -> stringResource(R.string.day_line, day)
                            })
                            // Delays go stale: when they were read (user, 2026-10-06).
                            if (f.delaysKnown) Text(
                                stringResource(R.string.delays_as_of, f.asOf.format(hourMinute)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (f.incomplete) Text(stringResource(R.string.not_all_checked), color = MaterialTheme.colorScheme.error)
                            if (f.noTimetable) Text(
                                stringResource(if (allDay) R.string.timetable_missing_all_day else R.string.timetable_missing),
                                color = MaterialTheme.colorScheme.error,
                            )
                            if (f.notInTimetable) Text(stringResource(R.string.not_in_timetable), color = MaterialTheme.colorScheme.error)
                            f.finds.forEach { FindCard(it, times, f.offered) }
                            f.fastest.forEach { FastestCard(it, times, f.offered) }
                            // Nothing faster: the official connection leaving first, to see where it changes (user, 2026-10-06).
                            if (nothing && first != null) Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.official_connection), style = MaterialTheme.typography.titleMedium)
                                    Trip(first, times, f.offered)
                                }
                            }
                        }
                    }
                    if (optimize && changes.isNotEmpty()) {
                        Heading(stringResource(R.string.optimization), optimizationOpen) { optimizationOpen = !optimizationOpen }
                        AnimatedVisibility(optimizationOpen) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.optimization_text), style = MaterialTheme.typography.bodySmall)
                                // A station on none of the trips shown is faded, still changeable (D3); with no
                                // result shown (searching, cancelled) none is.
                                val found = (result as? Result.Done)?.found
                                changes.forEach { stop ->
                                    key(stop.id) {
                                        var minutes by remember { mutableStateOf(prefs.getString(stop.id, null)?.toLongOrNull()) }
                                        MinutesStepper(
                                            stop.station, minutes, times.default(stop.id), times.official(stop.id), !searching,
                                            Modifier.alpha(if (found?.onTrips(stop.id) == false) FADED else 1f), station = true,
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
 * A panel's title (user, 2026-10-06), a heading a screen reader can jump to; with [open] given, a
 * tap folds or opens the panel and a ▾ or ▸ says which.
 */
@Composable
internal fun Heading(text: String, open: Boolean? = null, onClick: () -> Unit = {}) = Row(
    Modifier.fillMaxWidth().padding(top = 8.dp).then(if (open != null) Modifier.folding(open, onClick) else Modifier),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(text, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (open != null) FoldMark(open)
}

/** Folds or opens something on a tap; a screen reader says which it is (the ▾ or ▸ is hidden from it). */
@Composable
internal fun Modifier.folding(open: Boolean, onClick: () -> Unit, label: String? = null): Modifier {
    val state = stringResource(if (open) R.string.state_open else R.string.state_folded)
    return clickable(onClickLabel = label, onClick = onClick).semantics { stateDescription = state }
}

@Composable
internal fun FoldMark(open: Boolean) = Text(if (open) "▾" else "▸", Modifier.clearAndSetSemantics {})

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

/** The keyboard's key goes to the next field, or with [onSearch] given, searches (user, 2026-10-07). */
@Composable
private fun Field(
    label: String,
    value: String,
    enabled: Boolean,
    placeholder: String? = null,
    isError: Boolean = false,
    number: Boolean = false,
    onSearch: (() -> Unit)? = null,
    onValueChange: (String) -> Unit,
) = OutlinedTextField(
    value, onValueChange, Modifier.fillMaxWidth(), enabled,
    label = { Text(label) },
    placeholder = placeholder?.let { { Text(it) } },
    isError = isError,
    keyboardOptions = KeyboardOptions(
        keyboardType = if (number) KeyboardType.Number else KeyboardType.Unspecified,
        imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Next,
    ),
    keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
    singleLine = true,
)

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
    modifier: Modifier = Modifier,
    station: Boolean = false,
    range: LongRange = 0L..99,
    arrows: Boolean = true,
    onChange: (Long?) -> Unit,
) = Row(modifier, verticalAlignment = Alignment.CenterVertically) {
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
 * [from] → [to] at the next [leaving] (null: now), searched live with the rider's [times] ([find]),
 * and in the timetable, its [copy] downloaded when it's missing or old ([localTimetable]); or, [allDay],
 * the fastest of the day ([fastestOfTheDay]). Only the official connections' request failing fails
 * the search (any of the fastest of the day's); a failed onward request skips its change. Blocking.
 */
private fun search(from: String, to: String, leaving: LocalTime?, allDay: Boolean, times: TrackSwitchTimes, copy: File): Result = try {
    val ask = { a: String, b: String, at: LocalDateTime -> connections(a, b, at, BuildConfig.VERSION_NAME) }
    val timetable = {
        localTimetable(copy, { download(TIMETABLE_URL, BuildConfig.VERSION_NAME) }) { Log.w("Gleiswechsel", "Timetable: $it", it) }
    }
    val failed: (Exception) -> Unit = { Log.w("Gleiswechsel", "Not checked: $it", it) }
    Result.Done(
        if (allDay) fastestOfTheDay(from, to, times, ask, timetable, failed = failed)
        else find(from, to, leaving, times, ask, timetable, failed = failed),
    )
} catch (e: Exception) {
    // In the message too: Log drops the stack trace of an UnknownHostException (no network).
    Log.w("Gleiswechsel", "Search failed: $e", e)
    Result.Failed
}
