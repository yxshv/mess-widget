# Mess Menu widget

Android home-screen widget for the IIT Indore mess, styled after Samsung
Weather. It shows one meal at a time: the one being served, else the next.
The main dish is shown big, the rest underneath, and ‹ › step through earlier
and later meals (up to two days ahead). After each meal starts or ends the
widget snaps back to the meal that matters now. Tapping the card fetches the
latest menu.

The card colour follows the time of day: sunrise for breakfast, midday blue
for lunch, teal for Hi-Tea (paid, ₹20) and night for dinner. Resized to 2×2
it switches to a compact layout that still leads with the food.

Illustrations, card gradients and the launcher icon are generated from
`design/doodles.py`; `design/mock_*.png` are the mock-ups.

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
- `snacksPrice` — shown next to Hi-Tea.
- `caterer` — whose dishes to use when a menu email lists caterers separately.
- `validTo` — after this date the widget says "awaiting new menu".

## Build

```
./gradlew assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

Needs JDK 17 and the Android SDK (platform 34).
