package `in`.yxshv.messwidget

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class Meal(val key: String, val title: String) {
    BREAKFAST("breakfast", "Breakfast"),
    LUNCH("lunch", "Lunch"),
    SNACKS("snacks", "Hi-Tea"),
    DINNER("dinner", "Dinner"),
}

data class MealSlot(val meal: Meal, val start: LocalTime, val end: LocalTime, val items: String) {

    /**
     * The dish to show big, and the rest. The headline is the first dish that
     * isn't an everyday staple (rice, roti, plain dal, chutney...), so Monday
     * lunch reads "Rajma" rather than "Rice".
     */
    fun headlineAndSides(): Pair<String, List<String>> {
        val dishes = items.split(',', '+').map { it.trim() }.filter { it.isNotEmpty() }
        if (dishes.isEmpty()) return "" to emptyList()
        val main = dishes.indexOfFirst { !isStaple(it) }.coerceAtLeast(0)
        return dishes[main] to dishes.filterIndexed { i, _ -> i != main }
    }

    /** "12:30 – 2:30 PM", "5 – 6 PM", "11:30 AM – 1 PM". */
    fun timeRange(): String {
        val sameHalf = (start.hour < 12) == (end.hour < 12)
        return if (sameHalf) "${short(start)} – ${short(end)} ${half(end)}"
        else "${short(start)} ${half(start)} – ${short(end)} ${half(end)}"
    }

    fun startTime(): String = "${short(start)} ${half(start)}"

    private companion object {
        val STAPLE = Regex(
            "\\b(rice|roti|curd|papad|chutney|sprouts|raita|salad|pickle|nimbu|onion|chaas|lassi|" +
                "jaljeera|shikanji|rasam|tea|coffee|milk|bread|butter)\\b",
            RegexOption.IGNORE_CASE,
        )
        val PLAIN_DAL = Regex("\\bdal\\b", RegexOption.IGNORE_CASE)
        val SPECIAL_DAL = Regex("makhani|halwa", RegexOption.IGNORE_CASE)

        fun isStaple(dish: String) = STAPLE.containsMatchIn(dish) ||
            (PLAIN_DAL.containsMatchIn(dish) && !SPECIAL_DAL.containsMatchIn(dish))

        fun short(t: LocalTime): String {
            val h = (t.hour + 11) % 12 + 1
            return if (t.minute == 0) "$h" else "$h:${"%02d".format(t.minute)}"
        }

        fun half(t: LocalTime) = if (t.hour < 12) "AM" else "PM"
    }
}

data class DayMenu(val date: LocalDate, val slots: List<MealSlot>)

/** Parsed form of data/menu.json. */
class Menu(private val json: JSONObject) {

    /** Shown next to the Hi-Tea chip, since snacks are paid. */
    val snacksPrice: String = json.optString("snacksPrice")
    val validTo: LocalDate? = json.optString("validTo").takeIf { it.isNotEmpty() }?.let(LocalDate::parse)

    private val holidays: Set<String> = json.optJSONArray("holidays")
        ?.let { arr -> (0 until arr.length()).map { arr.getString(it) }.toSet() } ?: emptySet()

    fun dayMenu(date: LocalDate): DayMenu {
        val iso = date.toString()
        val scheduleKey = when {
            date.dayOfWeek == DayOfWeek.SUNDAY || iso in holidays -> "sunday"
            date.dayOfWeek == DayOfWeek.SATURDAY -> "saturday"
            else -> "weekday"
        }
        val timings = json.getJSONObject("timings").getJSONObject(scheduleKey)
        val week = json.getJSONObject("week").optJSONObject(date.dayOfWeek.name.lowercase())
        val override = json.optJSONObject("overrides")?.optJSONObject(iso)

        val slots = Meal.entries.mapNotNull { meal ->
            val range = timings.optJSONArray(meal.key) ?: return@mapNotNull null
            val items = override?.optString(meal.key)?.takeIf { it.isNotEmpty() }
                ?: week?.optString(meal.key).orEmpty()
            MealSlot(meal, LocalTime.parse(range.getString(0)), LocalTime.parse(range.getString(1)), items)
        }
        return DayMenu(date, slots)
    }

    /**
     * Today's and the next two days' meals in order, plus the index of the one
     * that matters now: the meal being served, else the next one.
     */
    fun timeline(now: LocalDateTime): Pair<List<Pair<LocalDate, MealSlot>>, Int> {
        val slots = (0L..2L).flatMap { offset ->
            val date = now.toLocalDate().plusDays(offset)
            dayMenu(date).slots.map { date to it }
        }
        val current = slots.indexOfFirst { (date, slot) -> date.atTime(slot.end).isAfter(now) }
        return slots to current.coerceAtLeast(0)
    }

    /** Next moment the widget's highlight should change. */
    fun nextBoundary(now: LocalDateTime): LocalDateTime {
        val times = (0..1).flatMap { offset ->
            val date = now.toLocalDate().plusDays(offset.toLong())
            dayMenu(date).slots.flatMap { listOf(it.start, it.end) }.map { date.atTime(it) }
        }
        return times.filter { it.isAfter(now) }.minOrNull() ?: now.plusHours(1)
    }

    companion object {
        val WEEKDAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE")

        fun parse(text: String) = Menu(JSONObject(text))
    }
}
