# Randomness

A couple of small, self-contained Android apps in one Gradle build.

| Module | App | What it is |
| --- | --- | --- |
| [`app`](app/README.md) | Note Guess | An ear-training game: it plays a note, you find it on the keyboard. |
| [`acremote`](acremote/README.md) | Mitsubishi AC Remote | An infrared remote control for Mitsubishi Electric split A/C units. |

## Building

Open the project in Android Studio and pick a run configuration, or build from
the command line:

```sh
./gradlew assembleDebug          # both apps
./gradlew :acremote:assembleDebug
./gradlew test                   # unit tests
```

APKs end up in `<module>/build/outputs/apk/debug/`. Requires an Android SDK
(compileSdk 34) and JDK 17+. Minimum supported device: Android 7.0 (API 24).
