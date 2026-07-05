# EzBolus — UI Design Brief

> Purpose: hand this to a designer (human or agent) so they can produce a
> coherent, sleek visual design without reading the source. This document
> describes **what exists today**, **what must not change** for safety
> reasons, and **what is genuinely up for redesign**.

---

## Status update (design has since been implemented)

This brief was written *before* the design was built. The design is now
implemented, and several statements below are stale. Where they conflict, the
notes here win:

- **Dynamic color is OFF, not on.** The app uses a fixed hunter-green / vanilla
  palette with full light **and** dark schemes (`Theme.kt`, `Color.kt`). §2 and
  §8's "dynamic color enabled" / "dark scheme minimal" are outdated.
- **The dose choice is a single `DoseStepper`** (big number + −/+ buttons that
  step by the pen increment), **not** the two-card round-down/round-up layout in
  §5.1 and §10. It seeds at the nearest deliverable dose. ⚠️ This departs from
  the §3.2 / §10 "must show both options as distinct tappable choices" safety
  rule — the stepper still forces a deliberate choice but no longer shows two
  cards side by side; confirm this is acceptable.
- **The live IOB indicator (`IobRing`) is built** — the "up for redesign" item in
  §9 is done.
- **A `FirstRunNudge` exists** (soft nudge toward Settings on defaults) — but the
  full first-run *setup flow* in §12 is still not built.
- **Now built (were "not built yet" in §12):** IOB threshold notification + exact
  alarm + boot re-arm, JSON/CSV export, and a home-screen IOB **widget** (new,
  not described anywhere in this brief).
- **Still not built:** onboarding/setup gate, hourly ICR/ISF/target, biometric
  lock, in-app permission priming, and any About/Privacy/disclaimer screen.
- **Data model note:** IOB is **pooled**, not meal/correction tagged — ignore any
  implication of a dose tag.

The safety constraints in §3 remain authoritative.

---

## 1. Product in one paragraph

EzBolus is a **personal, single-user Android insulin bolus calculator** the
developer is building for themselves. It computes meal and correction
insulin doses, tracks insulin-on-board (IOB) from past doses, and stores a
local history on-device. There are no accounts, no cloud, no other users.
The app assists a real dosing decision — a wrong number can cause severe
hypoglycemia — so it is **safety-critical**, and any visual redesign must
respect the constraints in §3.

Target device: modern Android phone (minSdk 31, targetSdk 36). Portrait
first; landscape acceptable but not primary. One-handed use is expected.

---

## 2. Existing tech constraints (structural, not visual)

- **Jetpack Compose + Material3** — the design should live inside Material3
  primitives (Scaffold, TopAppBar, Card, OutlinedTextField, FilterChip,
  SegmentedButton, Snackbar, LazyColumn). Custom Canvas work is fine for
  hero moments but should not replace standard inputs.
- **Dynamic color enabled on Android 12+** — the app currently opts into
  `dynamicColorScheme`, so on user devices the palette will match the
  wallpaper. The seed palette in `Color.kt` is only the fallback.
- **Light + dark theme** — dark scheme is minimal today (only primary +
  secondary defined). Dark deserves proper attention.
- **Edge-to-edge** — `enableEdgeToEdge()` is on. Design must respect the
  system bars (status + navigation).
- **Typography** — MaterialTheme defaults (Roboto). No custom scale yet;
  designer can propose one.
- **Icons** — Material Icons (Filled + AutoMirrored). Fine to swap for a
  custom icon set if it stays legible.

---

## 3. Non-negotiable UI constraints (safety)

These come from the project README and the user's medical context. Break
these and the app can cause a wrong dose.

1. **Insulin values display at most 1 decimal.** Never `2.53 U`; only
   `2.5 U`. The "exact" line, IOB, line-items, big dose numbers — all one
   decimal max. Pens that only take whole units render as `2 U`.
2. **The user always picks round-up vs round-down themselves.** If the
   exact dose is between two pen steps, the UI shows both options as
   distinct, tappable choices — never a single silently-rounded value.
3. **The carb bolus is never reduced by IOB.** Only the correction term
   is. Any visual grouping that implies IOB "eats" the carb portion is
   misleading and must not happen.
4. **Correction-only mode is a first-class state.** If the user leaves
   the carbs field blank, the calc changes to "suggested correction to
   \[target\]". The label must reflect this — no "Suggested dose" wording
   when carbs is blank.
5. **The IOB threshold notification must never imply action.** Copy is a
   plain factual statement of the number ("Insulin on board is below 2.0
   units"). Never "safe to eat", "take a correction now", "you can dose
   again", or similar.
6. **Confirmation is a real save.** Tapping a suggested dose card saves
   an insulin row (+ glucose + carbs rows) to the local database. That
   row immediately reduces future IOB. There must be an Undo, and the
   affordance ("tap to save") should be obvious — not a decorative
   surface the user might tap by accident.
7. **Every result screen carries a "this assists, does not replace"
   disclaimer.** Small type is fine; hiding it entirely is not.
8. **Two glucose units are supported (mg/dL and mmol/L).** All labels
   that mention a glucose value must show the currently-selected unit,
   including inside the dynamic strings (e.g. `Current glucose (mmol/L)`).

---

## 4. Navigation model

Three top-level screens, no bottom nav, no drawer, no tabs. Simple
state-based switch inside `MainActivity`.

```
                 ┌───────────────────┐
                 │    CALCULATOR     │ ← home / launcher target
                 │  (home screen)    │
                 └────┬─────────┬────┘
                      │         │
        History icon  │         │  Settings icon
                      ▼         ▼
             ┌──────────┐   ┌──────────┐
             │ HISTORY  │   │ SETTINGS │
             └──────────┘   └──────────┘
                 (back)         (back)
```

Only the Calculator has entry points into the others; History and
Settings both return via a back arrow / system back. No cross-linking
between History and Settings today (candidate for redesign).

---

## 5. Screens

### 5.1 Calculator (home)

**Purpose:** Enter carbs + glucose, receive a suggested insulin dose,
confirm (save) the actual dose taken.

**Layout (top → bottom):**

1. `TopAppBar`
   - Title: `EzBolus`
   - Actions (right): History icon (list), Settings icon (gear)
2. Vertically scrollable `Column`, 16 dp padding, 12 dp spacing
3. `OutlinedTextField` — **Carbs**
   - Label: `Carbs (g) — leave empty for correction only`
   - Decimal keyboard, single line
   - Accepts comma or dot as decimal separator (normalises to dot)
   - Empty carbs is a valid state (triggers correction-only mode)
4. `OutlinedTextField` — **Glucose**
   - Label: `Current glucose (mg/dL)` or `Current glucose (mmol/L)` —
     dynamic to the selected unit
   - Decimal keyboard, single line
   - Required — calc button surfaces an error otherwise
5. `RatiosCard` — small info card, "In use" summary
   - Line 1 (bodyMedium): `IOB: 2.5 U` — live, decayed through current curve
   - Line 2 (bodySmall): `ICR 1U : 10 g · ISF 1U : 2.2 mmol/L · target 5.3 mmol/L`
6. `Button` — **Calculate**, full-width, primary color
7. Error text (conditional, in error color) — e.g. `Enter your current glucose.`
8. **Result section** (conditional, appears only after a successful Calculate)
   - `HorizontalDivider`
   - `ResultCard` — filled with `primaryContainer`
     - Header (labelLarge): `Suggested dose` OR `Suggested correction to mmol/L 5.3`
     - Sub (bodyMedium): `Exact: 2.5 U`
     - Dose choice area, one of two layouts:
       - **On-step**: single wide `DoseOption` card, label `Tap to confirm`, big number
       - **Between steps**: `Row` of two `DoseOption` cards side by side, weight 1:1
         - Left: `Round down · tap to save`, big number
         - Right: `Round up · tap to save`, big number
     - Line items (fillMaxWidth, SpaceBetween):
       - `Carb bolus`     ...  `X.X U` (hidden in correction-only mode)
       - `Correction (raw)`     `X.X U`
       - `Correction after IOB` `X.X U`
   - Disclaimer (bodySmall): "Tap a dose to confirm you took it — it will
     be saved to your history and reduce future IOB. This tool assists
     your dosing decision, it does not replace your care team's guidance."
9. `SnackbarHost` (bottom edge, system-managed)
   - On save: `Saved 2 U`, action label `Undo`. Duration: short.
   - Undo action deletes all three rows (insulin + glucose + carbs) that
     share the just-created `takenAt` timestamp.

**Interaction summary:**
- Type carbs (optional) + glucose (required) → tap Calculate
- ResultCard appears; user taps one of the round options
- Rows saved to Room; inputs cleared; Snackbar shown with Undo
- Live IOB in RatiosCard updates within 30 s (bg tick) and immediately
  after a save

**Empty state:** No dedicated empty state — the two text fields + ratios
card + Calculate button are the entire UI when nothing has been calculated.

---

### 5.2 Settings

**Purpose:** Configure all user-editable dosing parameters. Values persist
in DataStore Preferences.

**Layout:** Scrollable `Column` of `SectionCard`s (title + divider + content).

**Section 1 — Glucose unit**
- Blurb (bodySmall): "Applies to target, ISF, and the glucose you type
  into the calculator. Switching auto-converts ISF and target so the
  clinical values stay the same."
- `SingleChoiceSegmentedButtonRow`, 2 options:
  - `mg/dL`
  - `mmol/L`
- On change, ISF and target auto-scale by 18.0182

**Section 2 — Dosing ratios**
- Three `OutlinedTextField`s, decimal keyboard, trailing suffix label:
  - `ICR — 1 U covers this many grams of carbs`  suffix `g / U`
  - `ISF — 1 U lowers glucose by`  suffix `mg/dL / U` or `mmol/L / U`
  - `Target glucose`  suffix `mg/dL` or `mmol/L`

**Section 3 — Insulin decay**
- Label: `Action time (hours)` + blurb "How long a dose is active.
  Confirm with your care team."
- 11 `FilterChip`s in two rows: 2.0, 2.5, 3.0 … 7.0
- Label: `Decay curve`
- `SingleChoiceSegmentedButtonRow` — `Linear` / `Bilinear` / `Exponential`

**Section 4 — Pen / pump increment**
- Blurb: "Smallest dose your device can deliver. The calculator shows
  round-down and round-up options in this step."
- `SingleChoiceSegmentedButtonRow` — `0.1 U` / `0.5 U` / `1 U (whole)`

**Section 5 — IOB alert**
- Row: label `Notify when insulin on board drops below the threshold` + `Switch`
- `OutlinedTextField` — `Threshold` (suffix `U`)
- Row: label `Do not re-alert until IOB rises again` + `Switch`

**TopAppBar:** title `Settings`, back arrow left.

---

### 5.3 History

**Purpose:** Show all saved intakes (insulin doses, glucose readings,
carb entries), newest first, grouped by day. Allow deleting individual
rows.

**Layout:** `LazyColumn`, 12 dp horizontal padding.

**Empty state:** centered column
- Title: `No entries yet`
- Body:  `Confirm a suggested dose on the calculator and it will appear here.`

**Populated:** For each day (reverse chronological):
- Day header (`titleSmall`, semi-bold, 4dp start pad):
  - `Today`, `Yesterday`, or `Monday 4 Nov 2026`
- For each entry that day (also newest first): `Card`, full width
  - `KindDot` — small 10 dp colored circle
    - insulin → `primary` color
    - glucose → `tertiary`
    - carbs   → `secondary`
  - Column (bodyLarge + bodySmall):
    - Value line: `2.5 U insulin`, `98 mg/dL glucose`, `45 g carbs`
    - Meta line (onSurfaceVariant): `14:32 · Insulin`
  - Trailing icon button: `Delete` (trash icon)

**TopAppBar:** title `History`, back arrow left.

---

## 6. Component catalogue (what exists to reuse)

Widgets currently in use — a designer should build on these before
introducing new patterns.

| Component | Where | Purpose | Notes |
|---|---|---|---|
| `Scaffold` + `TopAppBar` | All 3 screens | Frame + title bar + actions | Actions: History/Settings icons on Calculator; back arrow on the others |
| `OutlinedTextField` | Calculator, Settings | Numeric input | Decimal keyboard, singleLine, custom filter accepts digits + one `.` or `,` |
| `Button` (filled) | Calculator | Primary CTA "Calculate" | Full width |
| `Card` (elevated / filled) | Everywhere | Grouping | RatiosCard = plain, ResultCard = `primaryContainer`, DoseOption = `surface` |
| `HorizontalDivider` | Calculator | Separator above result section | Consider replacing with spacing in redesign |
| `FilterChip` | Settings | Action-time selector (11 values) | Currently two rows of 5-6 chips; ripe for a slider or wheel |
| `SingleChoiceSegmentedButtonRow` | Settings | Glucose unit, curve model, pen increment | Feels right for 2-3 options |
| `Switch` | Settings | Alert enabled + re-arm on rise | Standard |
| `Snackbar` + `SnackbarHost` | Calculator | "Saved X U · Undo" | Short duration |
| `LazyColumn` | History | Grouped scrolling list | Sticky headers not used today — could be nice |
| `Icon` (Material Icons) | Everywhere | Nav, actions, kind indicator | Settings gear, History list, Back arrow, Delete trash |
| Custom `KindDot` (10 dp circle) | History | Categorise entries at a glance | Small; could be bigger / iconified |

Custom composables (candidates for the design system):
- `SectionCard(title, content)` — Settings section wrapper
- `DoseOption(label, dose, increment, onClick)` — the big tappable dose card
- `RatiosCard(settings, iob)` — the "In use" summary
- `KindDot(kind)` — colored dot for entry kind
- `LineItem(label, value)` — SpaceBetween row inside ResultCard

---

## 7. Full copy inventory

Every user-facing string in the app, so the designer can plan around
text length and hierarchy.

**Top-level**
- `EzBolus` (app name / calculator title)
- `Settings`, `History` (screen titles)

**Calculator inputs**
- `Carbs (g) — leave empty for correction only`
- `Current glucose (mg/dL)` / `Current glucose (mmol/L)`

**Calculator ratios card**
- `In use`
- `IOB: {value} U`
- `ICR 1U : {n} g   ·   ISF 1U : {n} {unit}   ·   target {n} {unit}`

**Calculator CTA + errors**
- `Calculate`
- `Enter your current glucose.`
- `Carbs must be a number.`

**Result header**
- `Suggested dose`
- `Suggested correction to {unit} {target}`
- `Exact: {n} U`

**Dose choice card labels**
- `Tap to confirm`
- `Round down · tap to save`
- `Round up · tap to save`
- `{n} U` (large number)

**Result line items**
- `Carb bolus`, `Correction (raw)`, `Correction after IOB`

**Result disclaimer**
- "Tap a dose to confirm you took it — it will be saved to your history
  and reduce future IOB. This tool assists your dosing decision, it does
  not replace your care team's guidance."

**Save snackbar**
- `Saved {n} U`
- Action: `Undo`

**Settings sections and blurbs**
- Section titles: `Glucose unit`, `Dosing ratios`, `Insulin decay`,
  `Pen / pump increment`, `IOB alert`
- Glucose unit blurb: "Applies to target, ISF, and the glucose you type
  into the calculator. Switching auto-converts ISF and target so the
  clinical values stay the same."
- Ratio field labels:
  - `ICR — 1 U covers this many grams of carbs`
  - `ISF — 1 U lowers glucose by`
  - `Target glucose`
- Suffixes: `g / U`, `mg/dL / U`, `mmol/L / U`, `mg/dL`, `mmol/L`, `U`
- Decay labels: `Action time (hours)`, `Decay curve`
- Action time blurb: "How long a dose is active. Confirm with your care team."
- Increment blurb: "Smallest dose your device can deliver. The calculator
  shows round-down and round-up options in this step."
- Increment options: `0.1 U`, `0.5 U`, `1 U (whole)`
- Alert row: "Notify when insulin on board drops below the threshold"
- Re-arm row: "Do not re-alert until IOB rises again"
- Threshold field label: `Threshold`

**History**
- Empty title: `No entries yet`
- Empty body: `Confirm a suggested dose on the calculator and it will appear here.`
- Day headers: `Today`, `Yesterday`, `EEEE d MMM yyyy` (locale-formatted)
- Row values: `{n} U insulin`, `{n} mg/dL glucose` or `{n} mmol/L glucose`,
  `{n} g carbs`
- Row meta: `{HH:mm} · Insulin` / `· Glucose` / `· Carbs`

---

## 8. Current design system (baseline to improve on)

- **Palette (fallback, when dynamic color unavailable):**
  - Primary `#4C6FFF` (blue), OnPrimary `#FFFFFF`
  - PrimaryContainer `#DDE1FF`, OnPrimaryContainer `#00164E`
  - Secondary `#5A5D72`, OnSecondary `#FFFFFF`
  - Surface `#FEFBFF`, OnSurface `#1B1B1F`
  - Background `#FEFBFF`, OnBackground `#1B1B1F`
  - Error `#BA1A1A`, OnError `#FFFFFF`
- **Dark scheme:** currently minimal — only Primary and Secondary set.
  Everything else defaults to Material3 stock dark. This is a redesign
  target.
- **Typography:** Material3 defaults (Roboto).
- **Elevation:** default Material3 Card elevation, no custom.
- **Motion:** none — no shared-element transitions, no crossfades between
  screens (state switch is instant). Open for improvement.
- **Corner radius:** default Material3 (Card ~ 12dp).
- **Spacing scale in use:** 4, 6, 8, 12, 16 dp (informal, not systematised).

---

## 9. What is up for redesign (green-lit)

Anything not in §3 is open. Specifically encouraged:

- **Hero moment for the suggested dose.** The current ResultCard is
  functional but flat. The dose is the one number the user is here for
  — it can be much more prominent, with better visual distinction
  between round-down and round-up.
- **A live IOB indicator that reads at a glance.** Today it's one line
  of text in a small card. Consider a small chart / ring / bar that
  shows how much active insulin is on board and roughly how it will
  decay, updated as the 30 s tick fires.
- **Glucose unit toggle placement.** Buried in Settings today. Could be
  a global top-bar chip, or moved into the input row on the Calculator
  (`5.3 mmol/L ⌄`) so switching units is one tap.
- **Action-time selector.** 11 FilterChips in two rows is clunky.
  Slider, wheel picker, or two chip rows grouped by "short / medium /
  long" would all be better.
- **Empty state on Calculator.** There isn't one — first-time users
  see a bare form. A soft "Set up your ratios first" nudge with a
  button to Settings would be welcome when ICR/ISF/target are still at
  defaults.
- **History visual density.** Cards per row are heavy. Consider a
  timeline layout, a compressed list, or per-day mini-summary
  (`Today · 6.5 U total insulin · 2 corrections · 1 meal`).
- **Dark theme.** Currently under-designed. Deserves a full palette.
- **App icon.** Still stock Android green droid. A custom icon that
  reads at small sizes is welcome.
- **Motion.** Enter/exit transitions between screens, snackbar in,
  ResultCard reveal — all zero motion today.

---

## 10. Areas to avoid changing

- **Layout of the round-up / round-down dose choice.** The two-card
  side-by-side layout is deliberate: it forces the user to visually
  compare the two options. A single big number with a small toggle
  hides the choice and is unsafe. If the redesign changes this, it
  must still present both options with equal visual weight and
  independent tappable surfaces.
- **The disclaimer text under the result.** May be restyled, may not be
  removed or hidden behind a tap-to-expand.
- **Confirm-to-save being a single, unambiguous tap.** Two-step (select
  → confirm) or drag-to-save gestures increase the chance of missed
  saves, which are safety-relevant. If a redesign proposes a different
  confirm gesture, the visual affordance must be at least as clear as
  today's cards.
- **The word choice around the notification** (once it exists — not
  built yet). See §3.5.

---

## 11. Open questions for design (please answer / propose)

1. Does the "In use" ratios card belong on the Calculator, or is it too
   noisy and should be moved to a "..." on the top bar / a dedicated
   sheet?
2. When correction-only mode is active, should the whole screen look
   subtly different (color-shift, banner) so the user knows their
   result won't include carb coverage?
3. Should the History screen also render a **cumulative daily total**
   (insulin, carbs, glucose average) as day-header metadata?
4. Is `1 U (whole)` the right copy for a pen increment, or should we
   phrase it as `Whole units only` / `Pen (1 U)`?
5. Where should the future IOB threshold notification's setup live
   visually — is a full Settings section (as today) enough, or does it
   need a shortcut card on the Calculator when alerts are disabled?

---

## 12. What is NOT built yet (design can plan for it)

The following features are on the roadmap. Design ahead of code is
welcome — none of them exist in the app yet.

- **IOB threshold notification** — a heads-up notification when total
  IOB drops below a user-set number of units. Requires: an in-app entry
  point to grant `POST_NOTIFICATIONS` and exact-alarm permission, a
  visible state when alerts are disabled due to missing permission,
  and (out of app) the notification itself — heads-up, silent, no
  action wording.
- **Export / import** — JSON/CSV backup and restore for the local
  database. Needs an entry point (probably Settings) and a clear
  destination-picker flow.
- **Hourly ICR / ISF / target** — each of the three ratios may vary by
  hour of day. Settings currently only supports single-value mode. A
  design for editing 24 values per ratio is welcome (grid? sparkline
  editor? preset curves?).
- **Optional biometric lock on app open** — one toggle in Settings.
- **First-run setup flow** — nothing today. New user faces defaults
  (mg/dL, ICR 10, ISF 40, target 100). A guided walkthrough that lands
  them on Calculator with real values would improve the first
  experience a lot.
