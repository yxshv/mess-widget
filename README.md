# Mess Menu widget

Android home-screen widget showing today's IIT Indore mess menu: breakfast,
lunch, Hi-Tea (paid, ₹20) and dinner, with timings. The ongoing or next meal
is highlighted; after dinner it switches to tomorrow. Tap to refresh.

Styled to match a dark One UI home screen: charcoal card, grey rows,
coral-red accent.

## How the menu gets in

The widget reads `data/menu.json` from this repo's default branch
(`raw.githubusercontent.com/.../HEAD/data/menu.json`), caches it, and falls
back to the copy bundled in the APK when offline. Update the menu by editing
that file; phones pick it up within ~2 hours, or immediately on tap.

`menu.json` fields:

- `timings.weekday|saturday|sunday` — `[start, end]` per meal (24 h). Dates in
  `holidays` use Sunday timings.
- `week.<day>` — dishes per meal as display strings.
- `overrides."YYYY-MM-DD"` — one-day changes (e.g. "menu revised due to
  unavailability of vegetables" emails); only the meals listed are replaced.
- `validTo` — after this date the widget says "awaiting new menu".

## Build

```
./gradlew assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

Needs JDK 17 and the Android SDK (platform 34).
