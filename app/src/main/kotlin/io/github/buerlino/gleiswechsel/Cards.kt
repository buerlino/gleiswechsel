package io.github.buerlino.gleiswechsel

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.buerlino.gleiswechsel.core.Connection
import io.github.buerlino.gleiswechsel.core.Find
import io.github.buerlino.gleiswechsel.core.Leg
import io.github.buerlino.gleiswechsel.core.Minimums
import io.github.buerlino.gleiswechsel.core.Stop
import io.github.buerlino.gleiswechsel.core.ticketUrl
import io.github.buerlino.gleiswechsel.core.tooShort
import java.time.Duration
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
internal fun FindCard(find: Find, minimums: Minimums, rider: (Stop) -> Long) {
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
            var officialOpen by remember { mutableStateOf(false) }
            val uri = LocalUriHandler.current
            val context = LocalContext.current
            val ticket = ticketUrl(find.official.departure, find.official.arrival, stringResource(R.string.language))
            val noBrowser = stringResource(R.string.no_browser)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.folding(officialOpen, { officialOpen = !officialOpen }), verticalAlignment = Alignment.CenterVertically) {
                    ProvideTextStyle(MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        Text(stringResource(R.string.official_connection) + " ")
                        FoldMark(officialOpen)
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
            AnimatedVisibility(officialOpen) {
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
internal fun Trip(trip: Connection, minimums: Minimums, rider: (Stop) -> Long) {
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
private fun TrackSign(track: String) = Surface(color = BLUE) {
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

/**
 * A track switch time in a box, the number the app is about (user, 2026-10-06), coloured against
 * the [official] one, without a border (user, 2026-10-06); [faded] when it's a default. With
 * [arrows], an ↓ below the official one and an ↑ above, and a screen reader says which (user,
 * 2026-10-06: the colour alone is lost on colour-blind riders); the offset has its sign instead.
 */
@Composable
internal fun MinutesBox(minutes: Long, official: Long, faded: Boolean = false, minWidth: Dp = 0.dp, arrows: Boolean = true) =
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

/** The logo's blue: the track signs and Material's primary. */
internal val BLUE = Color(0xFF00179B)

/** A default track switch time's text, not set by the rider. */
internal const val FADED = 0.6f

/**
 * The box and its text: green below the official track switch time, orange at it, red above
 * (user, 2026-10-06), gridload's three with its text colours.
 */
private fun boxColors(minutes: Long, official: Long) = when {
    minutes < official -> Color(0xFF2E7D32) to Color.White
    minutes == official -> Color(0xFFFFA000) to Color.Black
    else -> RED to Color.White
}

internal val hourMinute = DateTimeFormatter.ofPattern("HH:mm")
