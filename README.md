# Note Guess

A tiny Android ear-training game.

## How to play

1. Tap **▶ Play a random note** — the app synthesizes and plays a random note
   from the C4–B4 octave.
2. Tap the note you think you heard (C, C♯, D, … B).
3. The app plays the note you picked, then tells you whether you were right
   (and what the answer was if you weren't), keeping a running score.
4. Use **🔁 Hear it again** to replay the mystery note before guessing.

All tones are synthesized on the fly with `AudioTrack` (a sine wave with a
couple of soft overtones), so there are no audio assets.

## Building

Open the project in Android Studio and run it, or build from the command line:

```sh
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`. Requires an Android SDK
(compileSdk 34) and JDK 17+. Minimum supported device: Android 7.0 (API 24).
