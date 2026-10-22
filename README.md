# Physio Helper

Personal Android app for a right-shoulder rehab routine. Everything is stored on the phone;
there is no account, server or sync.

## Install

Every push to `main` builds a signed APK and publishes it as a GitHub Release.
On the phone: open this repo's **Releases**, download the newest `PhysioHelper-N.apk`,
and install it (allow "install unknown apps" for the browser the first time).
New builds install over the old one and keep your data.

## What it does

- **Guided sessions**: the routine in order, with the exercise animation, cues and limits.
  Hold / rest / lift / lower timer with voice counts, beeps and vibration. Keeps running
  with the screen locked; can keep the screen awake.
- **Short "bad day" session**: isometrics + prone Y/T/W, about 15 minutes.
- **Per-exercise log**: full / partial / skipped, reps done, peak pain, effort, issue tags,
  note, "ask the physio" flag. A **Pain!** button mid-exercise enforces the pain limits.
- **Morning check-in**: pain, stiffness, night pain, warning signs. The reminder lets you
  reply with a pain score straight from the notification.
- **Weekly SPADI score**: the standard shoulder pain and disability questionnaire.
- **Analysis**: problem exercises ranked by pain, completion and issue tags; adherence and
  streak; morning flares; level-up and step-down suggestions.
- **Reminders**: session, morning check-in, evening nudge if skipped, weekly score, physio
  appointment. Restored after reboot. Includes an iQOO battery/autostart setup guide.
- **Export**: "Copy for Claude" summary, CSV of all logs, full JSON backup/restore,
  automatic weekly backup to `Downloads/PhysioHelper`.
- **Program editor**: change sets, reps, hold, rest, tempo, band, order; enable exercises
  once the physio confirms them; attach your own clips.

Animations: see [docs/ANIMATIONS.md](docs/ANIMATIONS.md).

## Build

Kotlin, Jetpack Compose, Room. `gradle :app:assembleRelease` with Gradle 8.9 and JDK 17,
or just push and let GitHub Actions build it.

The signing key in `signing/` exists only so successive builds can update each other on
the phone. Keep this repository private.
