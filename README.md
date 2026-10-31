# Mental Math Trainer (Android)

A Kotlin / Jetpack Compose app for daily mental-math practice on a phone: spaced-repetition
anchor facts, short technique lessons, interleaved timed drills from times tables up to logs,
roots, trig and Fermi estimates, a weekly benchmark, progress charts, audio practice (problems
read aloud by the system text-to-speech engine, answers typed), one-tap weak-spot practice, and
an optional daily reminder. Progress is local to the phone.

## Build and install

pixi supplies JDK 21 and `ANDROID_HOME`; the Gradle wrapper pins Gradle 8.11.1.

```sh
pixi run test            # core JVM tests: content, store, session
pixi run build-debug     # app/build/outputs/apk/debug/app-debug.apk
pixi run run             # build the release APK, install it on the connected phone, launch it
pixi run run-debug       # same for the debug build (appId dev.russell.mmtrainer.debug, installs alongside)
```

To install on a phone: enable Developer options → USB debugging (or Wireless debugging and
`adb pair`/`adb connect`), plug in, accept the prompt, then `pixi run run`. Without a
`keystore.properties` the release APK is debug-signed, which is fine for sideloading.

## Layout

```
core/      pure-Kotlin JVM module with all the learning logic
  py/          seeded Random (MT19937), locale-independent number formatting and round(), Fraction
  content/     skills and fact decks, one object per track (Arithmetic, Logs, …)
  Store.kt     SQLite persistence (behind the Db interface), spaced repetition, level promotion
  Session.kt   what to show next: lessons, blocked runs, interleaving, relearning
  Answers.kt   parsing typed answers
  Speech.kt    rich-text prompts as plain English for text-to-speech
app/       Android UI (Compose): bottom-nav sections (today, practice, library, progress), onboarding,
           lesson, drill with a custom keypad, summary, settings; audio/Speaker.kt wraps TextToSpeech,
           notify/Reminders.kt schedules the daily reminder
```
