# Mental Math Trainer (Android)

A Kotlin / Jetpack Compose port of the [Mental Math Trainer](../mental-math-trainer) desktop app,
for daily practice on a phone: spaced-repetition anchor facts, short technique lessons,
interleaved timed drills from times tables up to logs, roots, trig and Fermi estimates,
a weekly benchmark, and progress charts. Progress is local to the phone.

## Build and install

pixi supplies JDK 21 and `ANDROID_HOME`; the Gradle wrapper pins Gradle 8.11.1.

```sh
pixi run test            # core JVM tests: content, store, session (incl. golden parity with Python)
pixi run build-debug     # app/build/outputs/apk/debug/app-debug.apk
pixi run run             # build the release APK, install it on the connected phone, launch it
pixi run run-debug       # same for the debug build (appId dev.russell.mmtrainer.debug, installs alongside)
```

To install on a phone: enable Developer options → USB debugging (or Wireless debugging and
`adb pair`/`adb connect`), plug in, accept the prompt, then `pixi run run`. Without a
`keystore.properties` the release APK is debug-signed, which is fine for sideloading.

## Layout

```
core/      pure-Kotlin JVM module, a line-for-line port of the Python logic
  py/          CPython-exact Random (MT19937), float formatting/round(), Fraction
  content/     skills and fact decks, one object per track (Arithmetic, Logs, …)
  Store.kt     SQLite persistence (behind the Db interface), spaced repetition, level promotion
  Session.kt   what to show next: lessons, blocked runs, interleaving, relearning
  Answers.kt   parsing typed answers
app/       Android UI (Compose): home, lesson, drill with a custom keypad, summary,
           progress charts, library, free practice, settings
tools/golden/dump_golden.py   records fixtures from the Python app
```

## Parity with the Python app

The RNG is a bit-exact port of CPython's `random.Random`, and the formatting helpers reproduce
Python's `f"{x:.3f}"`, `:g`, `round()` and `str(float)`. So for the same seed the Kotlin generators
produce exactly the same problems, explanations and session flow as the desktop app.
`core/src/test/resources/golden/` holds fixtures recorded from the Python app (every skill level ×
2 seeds × 30 problems, every card and lesson, and six full session traces with the final database
state). The tests compare against them. After changing the Python content, run
`pixi run golden` (it reads `../mental-math-trainer`, or `MMT_PY_SRC`) and port the change.
