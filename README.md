# Note Guess

A tiny Android ear-training game.

## How to play

1. Tap **▶ Play a random note** — the app synthesizes and plays a random note
   from the C4–B4 octave.
2. Tap the key you think you heard on the on-screen piano keyboard. Keys are
   labelled in both solfège and letter notation (Do/C, Re/D, Mi/E, … Si/B,
   plus the black keys in between).
3. The app plays the note you picked, then tells you whether you were right
   (and what the answer was if you weren't), keeping a running score.
4. Use **🔁 Hear it again** to replay the mystery note before guessing.

## Hands-free driving mode

For ear training without touching or looking at the phone:

1. **🎙 Record voice answers** (one-time setup): record yourself saying each
   of the 12 answers in your own words ("that's do", "fa sharp", any
   language). Recordings stay on the device.
2. **🚗 Hands-free driving mode**: tap Start once. The app plays a note,
   listens for your spoken answer, and replies "Yes!" or "No, that was ray"
   — then plays the next note automatically.

Answers are matched against *your own* recordings (MFCC fingerprints compared
with dynamic time warping — `Dsp.kt`/`VoiceMatcher.kt`), entirely offline: no
speech-recognition service is involved, so accents and made-up words work
fine. If an answer is too close to call, the app asks "Say again?" instead of
guessing. The game pauses automatically if the app goes to the background.

All tones are synthesized on the fly with `AudioTrack` (a sine wave with a
couple of soft overtones), so there are no audio assets.

## Building

Open the project in Android Studio and run it, or build from the command line:

```sh
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/`. Requires an Android SDK
(compileSdk 34) and JDK 17+. Minimum supported device: Android 7.0 (API 24).
