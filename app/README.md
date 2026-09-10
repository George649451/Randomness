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

All tones are synthesized on the fly with `AudioTrack` (a sine wave with a
couple of soft overtones), so there are no audio assets.

## Building

```sh
./gradlew :app:assembleDebug
```
