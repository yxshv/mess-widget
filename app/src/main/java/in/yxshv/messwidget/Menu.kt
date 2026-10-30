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

data class MealSlot(val meal: Meal, val start: LocalTime, val end: LocalTime, val items: String)

data class DayMenu(val date: LocalDate, val slots: List<MealSlot>)

/** Parsed form of data/menu.json. */
class Menu(private val json: JSONObject) {

    val snacksLabel: String = json.optString("snacksLabel", "Hi-Tea")
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
     * The menu to show right now: today's, or tomorrow's once dinner is over.
     * Also returns the slot to highlight (ongoing, else next up).
     */
    fun current(now: LocalDateTime): Pair<DayMenu, MealSlot?> {
        val today = dayMenu(now.toLocalDate())
        val next = today.slots.firstOrNull { now.toLocalTime() < it.end }
        if (next != null) return today to next
        val tomorrow = dayMenu(now.toLocalDate().plusDays(1))
        return tomorrow to tomorrow.slots.firstOrNull()
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
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm")
        val AMPM: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

        fun parse(text: String) = Menu(JSONObject(text))
    }
}
