# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Mental Math Trainer for Android (`dev.russell.mmtrainer`): a Kotlin/Compose port of the PySide6
desktop app in `../mental-math-trainer`. The Python app is the reference implementation. See README.md.

## Commands

pixi tasks wrap `./gradlew` (Gradle 8.11.1, JDK 21 and `ANDROID_HOME` from pixi). Run raw Gradle
through pixi too, so it gets that environment.

- `pixi run test` runs `:core:test` (content, store, session, golden parity). There are no `:app` tests.
- Single test: `pixi run ./gradlew :core:test --tests 'dev.russell.mmtrainer.content.LogsTest'`
  (a method: append `.methodName`).
- `pixi run lint` runs Android lint on the debug variant.
- `pixi run build-debug` / `pixi run run-debug` build the debug APK, and install and launch it on the
  connected adb device (appId `dev.russell.mmtrainer.debug`, installs alongside release).
  `pixi run run` does the same for the minified release build (debug-signed without `keystore.properties`).
- `pixi run golden` regenerates the fixtures from the Python app (`MMT_PY_SRC`, default `../mental-math-trainer`).
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
  no navigation library). Every Store/Session call runs on `StoreThread`, a single thread, through
  `withContext`. `MmtApp` owns the one `Store`.
- Content strings are a small HTML subset rendered by `ui/RichText.kt` (supported tags are listed in
  `content/Base.kt`).

## Keep parity with Python

- The content modules are exact ports. Generators must call `Rng` methods in the same order as the
  Python code, and must format numbers only through `py/Py.kt` (`fmtF`, `fmtG`, `pyStr`, `pyRound`,
  `fmtNum`, …). Never use `String.format`, `Math.round` or `"$double"` in content code.
- The golden tests (`Golden.checkModule`, `SessionTest.matchesPythonSessionTraces`) compare against
  fixtures from `tools/golden/dump_golden.py`. Strings must match exactly; numbers may differ only in
  the last ulp. When the Python app changes, regenerate them with `pixi run golden` and port the
  change. Don't loosen the golden checks.
- Content tests also use `Claims.checkClaims` to verify the arithmetic written in explanations
  (port of the Python `tests/_claims.py`).
- Deliberate divergences: topic prerequisites (`Curriculum.PREREQUISITES`, `Store.prerequisiteMet`) exist
  only here. The session-trace parity test builds its Store with an empty prerequisite map, so
  everything else still matches Python exactly; the gating has its own tests in `SessionTest`.
  The `haptics`, `dynamic_color` and `auto_submit` settings are also Android-only, as is audio practice:
  `Speech.speakable` (spoken form of prompts; `SpeechTest` checks every generator's output reads as plain
  words) and the `sessions.audio` column, added by `ALTER TABLE` for older databases.
- Helpers in content objects stay private (or `internal` when shared, e.g. `Logs` → `Powers`). All
  content files share one package.
