# Changelog

Versions follow [semantic versioning](https://semver.org): patch for fixes, minor for new
features, major for changes that break existing data or habits. Each version below is a GitHub
release with its APK.

## [1.3.0] - 2026-10-24
### Added
- "Both sides" switch per exercise (Program → exercise): all sets on the right arm, then a
  switch countdown and the same on the left, with the demonstration mirrored. Off by default,
  since the prescription is for the right shoulder.

## [1.2.3] - 2026-10-24
### Fixed
- Hold countdown: voice cues now queue instead of interrupting each other, so "three, two, one"
  is heard before "Rest". Each count also beeps, and an offline voice is preferred to cut lag.
### Added
- Setting for how many seconds before the end of a hold the countdown starts.

## [1.2.2] - 2026-10-24
### Changed
- Session length is estimated from the program instead of a fixed "35–40 min".
### Fixed
- Discarded sessions no longer count toward per-exercise stats and advice.
- Export: exercises skipped every time say so instead of showing pain 0; empty discarded
  sessions are hidden.

## [1.2.1] - 2026-10-24
### Changed
- Wall 5 is a straight-arm palm push forward, the mirror of wall 1. Existing installs are
  updated on first launch (text only; dose and order kept).

## [1.2.0] - 2026-10-23
### Added
- Exercise clips for all 18 exercises (1.5 MB total), mirrored to the right arm where needed.

## [1.1.0] - 2026-10-23
### Added
- Redesign: bottom tabs, full-screen session with phase colours and a draining countdown ring,
  animated exercise figures synced to the timer, one-tap pain and effort logging, calendar,
  problem-exercise ranking.

## [1.0.0] - 2026-10-22
### Added
- Guided sessions with hold/rest timers and voice cues, per-exercise logging, morning
  check-in, weekly SPADI score, reminders with iQOO setup guide, analysis, export for Claude,
  CSV and JSON backup/restore, program editor.
