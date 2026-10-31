# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Mental Math Trainer for Android (`dev.russell.mmtrainer`): a Kotlin/Compose app for daily mental-math
practice. See README.md.

## Commands

pixi tasks wrap `./gradlew` (Gradle 8.11.1, JDK 21 and `ANDROID_HOME` from pixi). Run raw Gradle
through pixi too, so it gets that environment.

- `pixi run test` runs `:core:test` (content, store, session). There are no `:app` tests.
- Single test: `pixi run ./gradlew :core:test --tests 'dev.russell.mmtrainer.content.LogsTest'`
  (a method: append `.methodName`).
- `pixi run lint` runs Android lint on the debug variant.
- `pixi run build-debug` / `pixi run run-debug` build the debug APK, and install and launch it on the
  connected adb device (appId `dev.russell.mmtrainer.debug`, installs alongside release).
  `pixi run run` does the same for the minified release build (debug-signed without `keystore.properties`).
- `pixi run logs` shows filtered logcat.

## Architecture

- `:core` is plain Kotlin/JVM with no Android imports. All learning logic lives here, so it is
  testable on the JVM. `Store` talks to SQLite through the `Db` interface: `AndroidDb` in the app,
  `JdbcDb` (sqlite-jdbc) in tests.
- Content: each track is an `object` implementing `ContentModule` (`SKILLS`, `DECKS`) and registered in
  `Content.MODULES`. Skill, deck and card ids are stored in the database, so never rename them. A new
  topic also needs a place in `Curriculum.ORDER` (a test checks it covers all content) and usually
  entries in `Curriculum.PREREQUISITES`, which must point to topics earlier in `ORDER`.
- `Store` holds the learning model (SM-2-style card scheduling graded by correctness and speed, skill
  level promotion, settings). `Session` decides what to show next (lesson, blocked run, interleaved
  reviews, relearning missed facts) and is UI-agnostic. A "day" rolls over at 4 am local time.
- `:app` has a single `AppViewModel`, which is a screen state machine (`Screen` sealed interface,
  no navigation library). `AppRoot` animates between screens and shows the bottom bar on the
  `Screen.isTopLevel` sections. A screen stays composed while it transitions out, so it can't assume
  session state is still there. Every Store/Session call runs on `StoreThread`, a single thread, through
  `withContext`. `MmtApp` owns the one `Store`.
- Weak-spot practice: `Store.weakSpots` picks skills and individual facts, and `Session(focusCards = …)`
  drills those facts without moving their review schedule.
- `notify/Reminders.kt` holds the daily reminder: one inexact alarm, rescheduled each time it fires and
  by `ReminderReceiver` after a reboot, update or clock change.
- Content strings are a small HTML subset rendered by `ui/RichText.kt` (supported tags are listed in
  `content/Base.kt`).

## Content and tests

- Format numbers in content code only through `py/Py.kt` (`fmtF`, `fmtG`, `pyStr`, `pyRound`, `fmtNum`,
  …), never `String.format`, `Math.round` or `"$double"`: those depend on the locale or round differently.
  Generators draw from the `Rng` they're given, so seeded tests stay reproducible.
- Content tests use `Claims.checkClaims` to verify the arithmetic written in explanations, and
  `SpeechTest` checks every generator's output reads as plain words through `Speech.speakable`.
- Lessons are written for a phone: `<h3>` per technique, a short rule, `<ex>` for a worked example (one
  step per `<br>` line), `<tip>` for when to use it. `LessonsTest` checks their tags, arithmetic, thin-space
  thousands and paragraph length. Use `≈`, not `=`, for anything rounded; `=` claims must be exact.
- Cards can carry an `altPrompt` (the same fact asked another way, e.g. "6 × 2" for "2 × 6"), which
  `Session` shows half the time. `Session` also serves due facts in random order and draws new ones at
  random from the next `NEW_CARD_WINDOW` unseen cards of a deck, so one answer never cues the next.
- The `sessions.audio` column was added by `ALTER TABLE` for older databases; keep schema changes
  backward compatible the same way.
- Helpers in content objects stay private (or `internal` when shared, e.g. `Logs` → `Powers`). All
  content files share one package.
