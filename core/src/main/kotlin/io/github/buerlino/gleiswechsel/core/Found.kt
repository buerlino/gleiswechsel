package io.github.buerlino.gleiswechsel.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * A search's result, as the page shows it: the [day] and time asked for (Swiss time), the official
 * connection leaving [first] (null if there are none), the stations the search changed at
 * ([changes], each once: Optimization's rows), the [finds], the [shortest] change the planner's
 * answers make at each station ([Minimums.lowered]), whether some changes couldn't be checked
 * ([incomplete]), and when it searched ([asOf], Swiss time: when its delays were read).
 *
 * The last one is kept as JSON (user, 2026-10-06), so it's still there when the app opens again,
 * e.g. to read the track on a platform with poor reception.
 */
@Serializable
data class Found(
    @Serializable(with = LocalDateTimeText::class) val day: LocalDateTime,
    val first: Connection?,
    val changes: List<Stop>,
    val finds: List<Find>,
    val shortest: Map<String, Long>,
    val incomplete: Boolean,
    @Serializable(with = LocalDateTimeText::class) val asOf: LocalDateTime,
) {
    /** Whether any of its trips has a delay known, even 0: then the page says when they were read. */
    val delaysKnown: Boolean
        get() = (listOfNotNull(first) + finds.flatMap { listOf(it.official, it.faster) })
            .any { c -> c.legs.any { it.departure.delay != null || it.arrival.delay != null } }

    fun toJson(): String = json.encodeToString(this)
}

/** The [Found] in [text], from [Found.toJson]. Throws if it isn't one, e.g. an older app's format. */
fun found(text: String): Found = json.decodeFromString(text)

/** java.time as ISO text, e.g. `2026-10-07T08:53+02:00`. */
internal abstract class IsoText<T : Any>(private val parse: (String) -> T) : KSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor(javaClass.name, PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): T = parse(decoder.decodeString())
}

internal object OffsetDateTimeText : IsoText<OffsetDateTime>(OffsetDateTime::parse)

internal object LocalDateTimeText : IsoText<LocalDateTime>(LocalDateTime::parse)
