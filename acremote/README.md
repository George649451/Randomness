# Mitsubishi AC Remote

An Android remote control for Mitsubishi Electric split air conditioners,
laid out like the handset app it is modelled on: an LCD panel above five
controls.

## The remote

```
        ┌─────────────────────────────┐
        │          mitsubishi         │
        │              │   ❄ COOLING  │
        │    21  °C    ├──────────────┤
        │              │ ▮ WIND SPEED │
        └─────────────────────────────┘

             (⏻)      ⌃          (≋)
            ON/OFF   TEMP      WIND SPEED
             (⧉)      ⌄          (⇥)
             MODE               SWING
```

* **ON/OFF** — powers the unit on or off. The display blanks when it is off,
  like the real LCD.
* **MODE** — cycles COOLING → HEATING → DRY → FAN → AUTO, with the matching
  LCD icon.
* **TEMP** — 16 °C to 31 °C. The rocker is inert in DRY and FAN, where the
  unit ignores a setpoint, and the display shows `- -`.
* **WIND SPEED** — AUTO → four bar steps → SILENT.
* **SWING** — sweeps the vane; the button lights up while it is on.
* **?** — help, live IR diagnostics, and the code-set picker.

State is kept in `SharedPreferences`, so reopening the app shows the same
settings the A/C was last told about.

## Infrared

Mitsubishi units are stateless receivers: they do not acknowledge anything, and
each keypress re-transmits the *entire* state. So every button here encodes the
full frame and sends it through `ConsumerIrManager` at 38 kHz.

Transmitting requires `android.permission.TRANSMIT_IR` in the manifest —
`ConsumerIrService` enforces it on both `transmit()` and
`getCarrierFrequencies()`, and without it every call throws `SecurityException`
and nothing reaches the LED. Its protection level is `normal`, so declaring it
is enough; there is no runtime prompt.

### Code sets

"Mitsubishi" is a brand, not an encoding. Mitsubishi Electric alone ships three
incompatible A/C protocols, and Mitsubishi Heavy Industries — a different
company behind the same word on the box — ships two more. Headers, frame length
and checksum all differ, so a unit ignores frames from the wrong family
entirely. Which one an indoor unit speaks cannot be derived from its model name,
so the remote ships all three Electric encodings and lets you try them: **?** →
**Code set…**, then press ON/OFF. The unit beeps when it understands a frame.

| Code set | Frame | Checksum | Seen on |
| --- | --- | --- | --- |
| 144-bit (default) | 18 bytes, sent twice | low byte of bytes 0–16 | MSZ, MSC |
| 136-bit | 17 bytes | bytes 11–16 are bytes 5–10 inverted | MSZ-FH, GE |
| 112-bit | 14 bytes | low byte of bytes 0–12 | MSH, SEZ |

Mitsubishi Heavy Industries' 88- and 152-bit protocols are *not* implemented; if
none of the three above work, that family is the next thing to try.

### The 144-bit frame

| Byte | Meaning |
| --- | --- |
| 0–4 | fixed vendor prologue (`23 CB 26 01 00`) |
| 5 | power (bit 5) |
| 6 | mode, **bits 3–5** (`011` cool, `001` heat, `010` dry, `111` fan, `100` auto) |
| 7 | target temperature − 16 °C, low nibble |
| 8 | horizontal vane in the high nibble, mode bits in the low: `36` cool, `32` dry, `37` fan, `30` heat and auto |
| 9 | fan (bits 0–2), vane (bits 3–5), vane-set flag (bit 6), fan-auto flag (bit 7) |
| 10–16 | clock and timers, left unset here |
| 17 | checksum — low byte of the sum of bytes 0–16 |

Timings: 3400 µs/1750 µs header, 450 µs bit marks, 1300 µs/420 µs spaces for
one/zero, and a 440 µs mark plus 15500 µs gap between the two copies.

Byte layouts follow the protocol write-ups in
[IRremoteESP8266](https://github.com/crankyoldgit/IRremoteESP8266)'s
`ir_Mitsubishi.cpp`, whose bit fields are allocated from the least significant
bit up. Each code set reproduces that project's known-good reference frame byte
for byte, which `AcProtocolTest` pins — the closest thing to hardware
verification without a unit to point at. The reference frames it checks are:

```
144-bit  23 CB 26 01 00 20 08 06 30 45 (67 = handset clock, unset here)
136-bit  23 CB 26 21 00 40 C2 C7 04  + inverted tail
112-bit  23 CB 26 01 00 24 03 0B 10 00 00 00 30
```

**?** also shows the emitter status and the exact frame hex for the current
state, which is what to compare against a capture of your own handset.

## Building

```sh
./gradlew :acremote:assembleDebug
./gradlew :acremote:test        # encoder unit tests
```
