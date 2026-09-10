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

State is kept in `SharedPreferences`, so reopening the app shows the same
settings the A/C was last told about.

## Infrared

Mitsubishi units are stateless receivers: they do not acknowledge anything, and
each keypress re-transmits the *entire* state. So every button here encodes the
full frame and sends it through `ConsumerIrManager` at 38 kHz.

`MitsubishiIrEncoder` builds the Mitsubishi Electric "144 bit" frame (18 bytes,
sent twice) used by the MSZ/MSC units, following the protocol write-up in
[IRremoteESP8266's `ir_Mitsubishi.cpp`](https://github.com/crankyoldgit/IRremoteESP8266):

| Byte | Meaning |
| --- | --- |
| 0–4 | fixed vendor prologue (`23 CB 26 01 00`) |
| 5 | power (bit 5) |
| 6 | mode (bits 0–2) over a fixed `0x08` |
| 7 | target temperature − 16 °C |
| 9 | fan (bits 0–2), vane (bits 3–5), vane-set flag (bit 6) |
| 17 | checksum — low byte of the sum of bytes 0–16 |

Timings: 3400 µs/1750 µs header, 450 µs bit marks, 1300 µs/420 µs spaces for
one/zero, and a 440 µs/17100 µs footer between the two copies of the frame.

The frame layout is unit-tested (`MitsubishiIrEncoderTest`), but it has **not**
been checked against a real air conditioner — no hardware was available. If a
button does nothing on your unit, byte 6's mode values and byte 9's fan values
are the first things to compare against a capture of your own handset.

Transmitting needs a phone with an IR blaster; most no longer have one. Without
one the app says so in the status line and everything else still works, so the
layout can be checked on any device.

## Building

```sh
./gradlew :acremote:assembleDebug
./gradlew :acremote:test        # encoder unit tests
```
