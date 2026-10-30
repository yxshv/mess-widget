package `in`.yxshv.messwidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

class MessWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH, ACTION_TICK -> {
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

    override fun onDisabled(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(tickIntent(context))
    }

    companion object {
        private const val ACTION_REFRESH = "in.yxshv.messwidget.REFRESH"
        private const val ACTION_TICK = "in.yxshv.messwidget.TICK"
        private val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")
        private val DAY = DateTimeFormatter.ofPattern("EEEE, d MMM")

        private val ROWS = mapOf(
            Meal.BREAKFAST to Row(R.id.row_breakfast, R.id.title_breakfast, R.id.time_breakfast, R.id.items_breakfast),
            Meal.LUNCH to Row(R.id.row_lunch, R.id.title_lunch, R.id.time_lunch, R.id.items_lunch),
            Meal.SNACKS to Row(R.id.row_snacks, R.id.title_snacks, R.id.time_snacks, R.id.items_snacks),
            Meal.DINNER to Row(R.id.row_dinner, R.id.title_dinner, R.id.time_dinner, R.id.items_dinner),
        )

        private data class Row(val container: Int, val title: Int, val time: Int, val items: Int)

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MessWidgetProvider::class.java))
            if (ids.isEmpty()) return

            val menu = MenuRepository(context).cached()
            val now = LocalDateTime.now(ZONE)
            val views = render(context, menu, now)
            ids.forEach { manager.updateAppWidget(it, views) }
            scheduleTick(context, menu.nextBoundary(now))
        }

        private fun render(context: Context, menu: Menu, now: LocalDateTime): RemoteViews {
            val (day, highlight) = menu.current(now)
            val views = RemoteViews(context.packageName, R.layout.widget_mess)

            val heading = if (day.date == now.toLocalDate()) "Today" else "Tomorrow"
            views.setTextViewText(R.id.heading, heading)
            views.setTextViewText(R.id.date, day.date.format(DAY))
            val stale = menu.validTo?.let { day.date.isAfter(it) } == true
            views.setTextViewText(R.id.status, if (stale) "awaiting new menu" else "↻")

            for ((meal, row) in ROWS) {
                val slot = day.slots.firstOrNull { it.meal == meal }
                if (slot == null) {
                    views.setViewVisibility(row.container, android.view.View.GONE)
                    continue
                }
                views.setViewVisibility(row.container, android.view.View.VISIBLE)
                val active = slot == highlight
                val ongoing = active && day.date == now.toLocalDate() && now.toLocalTime() >= slot.start

                val title = if (meal == Meal.SNACKS) menu.snacksLabel else meal.title
                views.setTextViewText(row.title, if (active) "$title · ${if (ongoing) "now" else "next"}" else title)
                views.setTextViewText(row.time, "${slot.start.format(Menu.TIME)}–${slot.end.format(Menu.AMPM)}")
                views.setTextViewText(row.items, slot.items.ifEmpty { "Menu not synced yet" })
                views.setInt(row.container, "setBackgroundResource",
                    if (active) R.drawable.row_active else R.drawable.row_idle)
                views.setInt(row.items, "setMaxLines", if (active) 4 else 2)
            }

            views.setOnClickPendingIntent(R.id.root, refreshIntent(context))
            return views
        }

        private fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, MessWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun tickIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, MessWidgetProvider::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Re-render when a meal starts or ends so the highlight moves on time. */
        private fun scheduleTick(context: Context, at: LocalDateTime) {
            val millis = at.atZone(ZONE).toInstant().toEpochMilli()
            context.getSystemService(AlarmManager::class.java)
                .setWindow(AlarmManager.RTC, millis, 60_000, tickIntent(context))
        }
    }
}
