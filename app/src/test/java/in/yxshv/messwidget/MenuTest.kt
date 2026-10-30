package `in`.yxshv.messwidget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class MenuTest {

    private val menu = Menu.parse(File("../data/menu.json").readText())

    private fun currentAt(time: String): Pair<LocalDate, MealSlot> {
        val (slots, index) = menu.timeline(LocalDateTime.parse(time))
        return slots[index]
    }

    @Test
    fun servingMealIsCurrent() {
        val (date, slot) = currentAt("2026-10-30T08:08")
        assertEquals(LocalDate.parse("2026-10-30"), date)
        assertEquals(Meal.BREAKFAST, slot.meal)
    }

    @Test
    fun betweenMealsTheNextOneIsCurrent() {
        assertEquals(Meal.SNACKS, currentAt("2026-10-30T15:00").second.meal)
        assertEquals(Meal.DINNER, currentAt("2026-10-30T18:30").second.meal)
    }

    @Test
    fun afterDinnerMovesToTomorrowsBreakfastWithWeekendTimings() {
        val (date, slot) = currentAt("2026-10-30T22:00")
        assertEquals(LocalDate.parse("2026-10-31"), date)
        assertEquals(Meal.BREAKFAST, slot.meal)
        assertEquals("08:30", slot.start.toString())
    }

    @Test
    fun saturdayDinnerUsesSaturdayTimings() {
        val sat = menu.dayMenu(LocalDate.parse("2026-10-31")).slots.first { it.meal == Meal.DINNER }
        assertEquals("19:30", sat.start.toString())
    }

    @Test
    fun headlineSkipsStaples() {
        fun headline(date: String, meal: Meal) =
            menu.dayMenu(LocalDate.parse(date)).slots.first { it.meal == meal }.headlineAndSides().first
        assertEquals("Rajma", headline("2026-10-26", Meal.LUNCH))
        assertEquals("Pav Bhaji", headline("2026-10-30", Meal.DINNER))
        assertEquals("Poha", headline("2026-10-30", Meal.BREAKFAST))
        assertEquals("Veg Pasta", headline("2026-10-30", Meal.SNACKS))
        assertEquals("Kadhi", headline("2026-10-30", Meal.LUNCH))
    }

    @Test
    fun timeRangesAreCompact() {
        val day = menu.dayMenu(LocalDate.parse("2026-10-30")).slots.associateBy { it.meal }
        assertEquals("7:45 – 10 AM", day.getValue(Meal.BREAKFAST).timeRange())
        assertEquals("12:30 – 2:30 PM", day.getValue(Meal.LUNCH).timeRange())
        assertEquals("5 – 6 PM", day.getValue(Meal.SNACKS).timeRange())
    }
}
