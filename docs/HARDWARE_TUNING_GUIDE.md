# Hardware Tuning Guide (Public Alpha)

This mod generates **low-frequency audio** intended for a **bass shaker / tactile transducer**.

## Safety first

- Start with **very low volume** (both in-game and in your OS / DAC / amp).
- If anything **bottoms out**, rattles harshly, or gets hot: **turn it down immediately**.
- Avoid long sessions at high intensity; tactile rigs can stress mounts and furniture.

## Recommended signal chain

- Minecraft → (this mod) → audio device / DAC → amp → bass shaker
- Prefer a dedicated output device (USB DAC) if you can, so game audio and shaker audio don’t fight each other.

## Low-pass filtering (strongly recommended)

Bass shakers behave best when you remove higher frequencies.

- Suggested low-pass cutoff: **60–120 Hz**
- Suggested slope: **12–24 dB/oct**

Where to apply it:
- Hardware DSP (amp / receiver) if available
- OS-level EQ / DSP
- DAC/amp driver DSP (if it has one)

## Latency & buffer tuning

This mod uses JavaSound. Different devices/drivers accept different buffer sizes.

- Use Advanced settings → Audio → **Latency test** to compare “feel vs. visuals”.
- Use Advanced settings → Audio → **JavaSound buffer** to trade off stability vs. latency.
  - Smaller buffer: lower latency, higher risk of underruns/clicks
  - Larger buffer: more stable, more latency

Tip:
- If you hear clicks/pops under load, try increasing the buffer size one step.

## Common USB DAC quirks

- Some USB DAC drivers ignore requested buffer sizes; the mod logs the **accepted** buffer.
- USB power saving can cause dropouts; consider disabling selective suspend for troubleshooting.
- Bluetooth audio usually adds too much latency for tactile sync.

## Intensity tuning checklist

- If everything feels “washed out”: reduce overall volume and then increase only the events you care about.
- If explosions dominate: reduce explosion gain or priority in the profile JSON.
- If you get double-feels: enable the debug overlay and capture suppression lines for the report.

## What to include in bug reports

Please include:

- OS version
- Audio device / DAC model + driver
- Amp model (if any)
- Whether you use low-pass filtering and the cutoff
- A screenshot/photo of the **Debug overlay** when the issue occurs
- A log snippet showing the **requested vs accepted** JavaSound buffer

(There is a GitHub bug report template that prompts for these.)
