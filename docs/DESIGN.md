# Physio Helper — product design

## Who it's for

One person doing a daily physio routine at home for months: 35–40 minutes, about 15 exercises,
most of them holds. The real risk isn't doing an exercise wrong; it's quitting at week 3–4 when the
pain eases and the routine starts to feel optional. Every design choice below serves one goal:
**make the routine easy to start, impossible to get lost in, and honest about progress.**

## The four moments that matter

| Moment | Context | What the user needs | Design response |
|---|---|---|---|
| **Starting** (evening) | Tired, phone in hand | One obvious action, no decisions | Today screen is one big Start button with the week's progress around it. A "bad day" short session is right below it, so a busy day becomes a short session, not a skipped one. |
| **During a set** | Phone on the floor 1–2 m away, lying face-down for half the exercises | Read the state from a distance or not at all | Full-screen session mode: the whole screen changes colour by phase, a huge countdown in a ring, an animated figure that moves in sync with the timer, and voice counts so the screen is optional. Three large controls only: Pain, Pause, Skip. |
| **Logging** (after each exercise) | Slightly out of breath, wants to move on | Under 5 seconds | Pain is a row of 11 tappable numbers (no fiddly slider), effort is three big buttons, issues are chips, everything else is folded away. Reps are pre-filled from the timer. |
| **Morning check** | Just woke up | One tap | Answer from the notification itself, or tap a number on the Today card. Details (stiffness, night pain, warning signs) are optional. |

Secondary moments: weekly score (SPADI), reviewing progress, changing the program after a physio
visit, exporting for analysis. These live one level down and can take a few more taps.

## Information architecture

Bottom navigation with four tabs; anything task-like opens full-screen above it.

- **Today**: start session, week ring and streak, morning check, this week's days, one insight.
- **Progress**: Overview (calendar, consistency, mornings, flares, SPADI) · Exercises (problem
  exercises first, level-up / step-down advice) · History (sessions).
- **Program**: the exercise list with live figures, enable/disable, reorder, edit dose.
- **More**: weekly score, physio questions, export & backup, settings, reminder setup, warning signs.

Full-screen flows: Session, Check-in, Weekly score, Session detail, Edit exercise.

## Visual language

- **Colour carries state.** Hold = teal, lift/lower = blue, rest = sand, get ready = slate.
  Pain uses a fixed green → amber → red scale everywhere (0–3 fine, 4–5 caution, 6+ stop),
  matching the physio's 3/10 rule.
- **Big type for numbers.** Timers, counts and scores are the largest thing on any screen.
- **Rounded, calm surfaces.** Large-radius cards, generous spacing, no visual noise during sessions.
- **Figures, not photos.** Exercise figures are simple line drawings in the theme colours. The
  working arm is highlighted; force arrows show push direction for isometrics; a dashed line marks
  the shoulder-height limit on raises. They're original, so the repo can stay public, and they
  show exactly the constraints that matter. A clip recorded by the user overrides the figure.

## Motion

Motion explains state; it never decorates for its own sake.

- The session background cross-fades between phase colours, so a glance across the room tells you
  hold vs rest.
- The countdown digits slide on each second; the ring drains smoothly.
- The figure lifts during "lift", holds during "hold", lowers during "lower". For isometrics the
  force arrow pulses while you push.
- Screen transitions slide in the direction of travel (deeper = from the right, back = to the right).
- The week ring and day dots animate in when the Today screen opens, so progress feels earned.
- Finishing a session gets one celebratory moment (check mark burst). Nothing else celebrates.
- Haptic tick on every pain/effort tap.

## Safety built into the UI

- The **Pain!** button is always visible mid-exercise. Above 5/10 the app tells you to stop.
- Warning signs (numbness, swelling, colour change, shaking episodes, hours-long pain) are part of
  the morning check; ticking one shows a stop message and a red card on Today.
- Exercises the physio hasn't confirmed ship disabled and labelled "pending physio".
- Level-up suggestions only change sets or band colour; anything new says "ask the physio".

## Not doing (on purpose)

Accounts, sync, social, badges, charts with axes, gamified points. A streak and a calendar are
enough motivation; more would turn rehab into a game you can "win" by overdoing it.
