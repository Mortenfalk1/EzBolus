# EzBolus

**Offline insulin bolus calculator for Android, built in Kotlin & Jetpack Compose — a personal replacement for the deprecated mylife Diabetescare bolus calculator.**

EzBolus computes meal and correction insulin doses, tracks insulin‑on‑board (IOB)
from your recent doses, keeps a local history, and can nudge you with a heads‑up
notification when your active insulin drops below a threshold. Everything runs
**on‑device** — no accounts, no cloud, no network required.

> [!WARNING]
> **EzBolus is a personal tool, not a certified medical device.**
> Insulin dosing errors can cause severe hypoglycemia, which can be dangerous or
> fatal. Every dosing parameter (ICR, ISF, target glucose, insulin action time,
> decay curve) **must be confirmed with your own doctor or diabetes care team**
> before real use, and every result should be sanity‑checked against a known‑good
> reference. The app **assists** a dosing decision — it never replaces your
> judgment or your care team's guidance. Use at your own risk.

---

## Why this exists

The **mylife Diabetescare** bolus calculator was deprecated and no longer works.
EzBolus is a self‑built, native Android replacement that reproduces the dosing
math I relied on — offline, private, and tuned to my own therapy settings.

---

## Features

- **Meal + correction dosing** — carb bolus (`carbs ÷ ICR`) plus a correction
  (`(glucose − target) ÷ ISF`), reduced by insulin on board.
- **Correction‑only mode** — leave carbs blank to get just the correction needed
  to reach your target.
- **Insulin‑on‑board tracking** — a pooled IOB model with three selectable decay
  curves: **linear**, **bilinear**, and **exponential** (OpenAPS‑style), over a
  configurable 2–7 h action time.
- **You always choose the rounding** — the result shows round‑down and round‑up
  options for your device's increment (0.1 / 0.5 / 1 U), with a −/+ fine‑tune and
  an explicit Save step. Doses never silently round.
- **Two glucose units** — mg/dL and mmol/L, with automatic conversion when you
  switch so the clinical values stay identical.
- **Local history** — every saved dose, glucose, and carb entry, grouped by day.
- **Low‑IOB notification** — an optional heads‑up alert (gentle/silent or
  sound + vibrate) when active insulin falls below a threshold you set.
- **Home‑screen widget** — a live IOB ring, glanceable without opening the app.
- **JSON / CSV export** — back up your on‑device data off the phone.
- **Light & dark themes** — a fixed hunter‑green / vanilla palette.

---

## Screenshots

> _Add screenshots here (e.g. in `docs/screenshots/`) — calculator, result page,
> settings, history, and the widget._

---

## How the dose is calculated

```
carbBolus          = carbs ÷ ICR                          // never reduced by IOB
rawCorrection      = (glucose − target) ÷ ISF             // can be ≤ 0
correctionAfterIob = max(0, rawCorrection − IOB)          // floored at zero
total              = carbBolus + correctionAfterIob
```

IOB is **pooled** (all active insulin from recent doses) and only ever reduces
the *correction* portion — a meal's carb coverage is always delivered in full.
The final amount is rounded to your pen/pump increment only at the very end, and
you pick round‑down vs round‑up yourself.

---

## Tech stack

| Concern | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Dosing engine | Pure Kotlin, dependency‑free, unit‑tested |
| Local storage | Room (history) + DataStore (settings) |
| Notifications | AlarmManager (exact alarms) → heads‑up notification |
| Widget | Jetpack Glance |
| Min / target SDK | 31 / 36 |

No backend, no analytics, no third‑party accounts.

---

## Building

1. Clone the repo and open it in a recent **Android Studio**.
2. Let Gradle sync, then run the `app` configuration on a device or emulator
   (Android 12 / API 31 or newer).
3. Grant the notification and exact‑alarm permissions if you want the low‑IOB
   alert to fire.

Run the engine tests with:

```bash
./gradlew test
```

The dosing math (`app/.../engine`) is covered by JVM unit tests with
hand‑calculated expected values — treat a failing engine test as a release
blocker.

---

## Project layout

```
app/src/main/java/com/ostemirt/ezbolus/
  engine/     pure dosing math (Bolus, Iob, Crossing, IcrIsf)
  data/       Room database, DataStore settings, export
  notify/     IOB threshold alarm + heads-up notification
  widget/     home-screen IOB ring (Glance)
  ui/         Compose screens: calculator, history, settings
docs/DESIGN.md  detailed design & build spec
```

---

## Disclaimer & license

EzBolus is provided **as‑is, with no warranty of any kind**, for personal and
educational use. It is not a medical device and has not been reviewed or
approved by any regulatory body. See the warning at the top — always confirm
doses with your care team.

_License: see `LICENSE` 
