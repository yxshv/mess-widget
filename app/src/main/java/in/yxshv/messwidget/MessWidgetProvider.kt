package `in`.yxshv.messwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.concurrent.thread

class MessWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PREV, ACTION_NEXT -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                val step = if (intent.action == ACTION_NEXT) 1 else -1
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                prefs.edit().putInt(offsetKey(id), prefs.getInt(offsetKey(id), 0) + step).apply()
                update(context, AppWidgetManager.getInstance(context), id)
            }
            ACTION_REFRESH, ACTION_TICK -> {
                if (intent.action == ACTION_TICK) resetOffsets(context)
                val force = intent.action == ACTION_REFRESH
                val pending = goAsync()
                thread {
                    try {
                        MenuRepository(context).refresh(force)
                        updateAll(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Draw immediately from cache, then try to fetch a newer menu.
        updateAll(context)
        val pending = goAsync()
        thread {
            try {
                if (MenuRepository(context).refresh(force = false)) updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle,
    ) = update(context, manager, id)

    override fun onDeleted(context: Context, ids: IntArray) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        ids.forEach { edit.remove(offsetKey(it)) }
        edit.apply()
    }

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(broadcast(context, ACTION_TICK, 0))
    }

    companion object {
        private const val ACTION_REFRESH = "in.yxshv.messwidget.REFRESH"
        private const val ACTION_TICK = "in.yxshv.messwidget.TICK"
        private const val ACTION_PREV = "in.yxshv.messwidget.PREV"
        private const val ACTION_NEXT = "in.yxshv.messwidget.NEXT"
        private const val PREFS = "widget"
        private const val LARGE_MIN_WIDTH_DP = 240
        private val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

        private fun offsetKey(id: Int) = "offset_$id"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MessWidgetProvider::class.java))
            if (ids.isEmpty()) return
            ids.forEach { update(context, manager, it) }
            val now = LocalDateTime.now(ZONE)
            scheduleTick(context, MenuRepository(context).cached().nextBoundary(now))
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val menu = MenuRepository(context).cached()
            val now = LocalDateTime.now(ZONE)
            val small = render(context, menu, now, id, R.layout.widget_small)
            val large = render(context, menu, now, id, R.layout.widget_large)
            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RemoteViews(mapOf(SizeF(110f, 110f) to small, SizeF(LARGE_MIN_WIDTH_DP.toFloat(), 110f) to large))
            } else {
                val width = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                if (width >= LARGE_MIN_WIDTH_DP) large else small
            }
            manager.updateAppWidget(id, views)
        }

        private fun render(
            context: Context, menu: Menu, now: LocalDateTime, id: Int, layout: Int,
        ): RemoteViews {
            val compact = layout == R.layout.widget_small
            val (slots, current) = menu.timeline(now)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val index = (current + prefs.getInt(offsetKey(id), 0)).coerceIn(slots.indices)
            val (date, slot) = slots[index]
            val views = RemoteViews(context.packageName, layout)

            val (card, art) = when (slot.meal) {
                Meal.BREAKFAST -> R.drawable.card_breakfast to R.drawable.art_breakfast
                Meal.LUNCH -> R.drawable.card_lunch to R.drawable.art_lunch
                Meal.SNACKS -> R.drawable.card_snacks to R.drawable.art_snacks
                Meal.DINNER -> R.drawable.card_dinner to R.drawable.art_dinner
            }
            views.setInt(R.id.root, "setBackgroundResource", card)
            views.setImageViewResource(R.id.art, art)

            views.setTextViewText(R.id.status, statusText(date, slot, now, index, current))
            views.setTextViewText(R.id.meal, slot.meal.title)
            val ongoing = date == now.toLocalDate() && now.toLocalTime() in slot.start..slot.end
            views.setTextViewText(R.id.time, when {
                ongoing -> "till ${slot.timeRange().substringAfter("– ")}"
                compact -> slot.startTime()
                else -> slot.timeRange()
            })

            val (headline, sides) = slot.headlineAndSides()
            val stale = menu.validTo?.let { date.isAfter(it) } == true
            views.setTextViewText(R.id.headline, headline.ifEmpty { "Menu not out yet" })
            views.setTextViewText(R.id.sides, when {
                headline.isNotEmpty() -> sides.joinToString("\u00A0• ")
                stale -> "The next menu hasn't been emailed yet"
                else -> ""
            })
            if (compact) {
                // A two-line dish name leaves room for one line of sides.
                views.setInt(R.id.sides, "setMaxLines", if (headline.length > 13) 1 else 2)
            }

            views.setViewVisibility(R.id.prev, if (index > 0) View.VISIBLE else View.INVISIBLE)
            views.setViewVisibility(R.id.next, if (index < slots.lastIndex) View.VISIBLE else View.INVISIBLE)
            views.setOnClickPendingIntent(R.id.prev, broadcast(context, ACTION_PREV, id))
            views.setOnClickPendingIntent(R.id.next, broadcast(context, ACTION_NEXT, id))
            // Tapping the card itself fetches the latest menu; no visible reload button.
            views.setOnClickPendingIntent(R.id.root, broadcast(context, ACTION_REFRESH, 0))
            return views
        }

        /** "Now" / "Next" for the meal that matters, else "Earlier", "Later", "Tomorrow" or the weekday. */
        private fun statusText(date: LocalDate, slot: MealSlot, now: LocalDateTime, index: Int, current: Int): String {
            val today = now.toLocalDate()
            return when {
                index == current && date == today && now.toLocalTime() >= slot.start -> "Now"
                index == current -> if (date == today) "Next" else "Next · ${dayName(date, today)}"
                date == today -> if (index < current) "Earlier" else "Later"
                else -> dayName(date, today)
            }
        }

        private fun dayName(date: LocalDate, today: LocalDate) =
            if (date == today.plusDays(1)) "Tomorrow" else date.format(Menu.WEEKDAY)

        private fun broadcast(context: Context, action: String, id: Int): PendingIntent =
            PendingIntent.getBroadcast(
                // Intents differ by action; the request code keeps each widget's buttons apart.
                context, id,
                Intent(context, MessWidgetProvider::class.java)
                    .setAction(action)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        /** When a meal starts or ends, jump back to the meal that matters now. */
        private fun resetOffsets(context: Context) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val edit = prefs.edit()
            prefs.all.keys.filter { it.startsWith("offset_") }.forEach { edit.remove(it) }
            edit.apply()
        }

        private fun scheduleTick(context: Context, at: LocalDateTime) {
            val millis = at.atZone(ZONE).toInstant().toEpochMilli()
            context.getSystemService(AlarmManager::class.java)
                .setWindow(AlarmManager.RTC, millis, 60_000, broadcast(context, ACTION_TICK, 0))
        }
    }
}
