# CLAUDE.md

Mental Math Trainer for Android (`dev.russell.mmtrainer`): a Kotlin/Compose port of the PySide6
desktop app in `../mental-math-trainer`. The Python app is the reference implementation. See README.md.

## Commands

pixi tasks wrap `./gradlew` (Gradle 8.11.1, JDK 21 from pixi). `pixi run test` runs `:core:test`.
Single test: `./gradlew :core:test --tests 'dev.russell.mmtrainer.content.LogsTest'`.
`pixi run build-debug` / `pixi run run-debug` build and install on the connected adb device.

## Architecture

- `:core` is plain Kotlin/JVM with no Android imports. All learning logic lives here, so it is
  testable on the JVM. `Store` talks to SQLite through the `Db` interface: `AndroidDb` in the app,
  `JdbcDb` (sqlite-jdbc) in tests.
- `:app` has a single `AppViewModel`, which is a screen state machine (no navigation library).
  Every Store/Session call runs on `StoreThread`, a single thread, through `withContext`.
- Content strings are a small HTML subset rendered by `ui/RichText.kt`.

## Keep parity with Python

- The content modules are exact ports. Generators must call `Rng` methods in the same order as the
  Python code, and must format numbers only through `py/Py.kt` (`fmtF`, `fmtG`, `pyStr`, `pyRound`,
  `fmtNum`, …). Never use `String.format`, `Math.round` or `"$double"` in content code.
- The golden tests (`Golden.checkModule`, `SessionTest.matchesPythonSessionTraces`) compare against
  fixtures from `tools/golden/dump_golden.py`. When the Python app changes, regenerate them with
  `pixi run golden` and port the change. Don't loosen the golden checks.
- Helpers in content objects stay private (or `internal` when shared, e.g. `Logs` → `Powers`). All
  content files share one package.
