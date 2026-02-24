namespace BstHapticEngine;

public sealed class HapticSynth
{
	private const float PanGamma = 0.75f;
	private readonly EngineConfig _cfg;
	private readonly object _lock = new();
	private readonly List<Voice> _voices = new();
	private readonly Random _rng = new(12345);
	private long _sampleCursor;
	private volatile TelemetryPacket _telemetry;
	private WindState _wind;

	public HapticSynth(EngineConfig cfg)
	{
		_cfg = cfg;
		_wind = new WindState(false, 0f, 0f, "movement");
	}

	public void SetTelemetry(TelemetryPacket telemetry) => _telemetry = telemetry;

	public void TriggerVoice(EffectSpec effect, HapticPacket packet, float busGain, float masterGain, int globalDelayMs)
	{
		var startDelayMs = Math.Max(0, globalDelayMs + packet.DelayMs);
		var startSample = _sampleCursor + (long)((startDelayMs / 1000.0) * _cfg.SampleRate);
		var durSamples = Math.Max(1, (int)((packet.Ms / 1000.0) * _cfg.SampleRate));
		var pan = ComputePan(effect, packet.AzimuthDeg, packet.DirectionBand);
		var gain = packet.Gain * effect.Gain * busGain * masterGain;

		lock (_lock)
		{
			_voices.Add(new Voice(
				startSample,
				durSamples,
				packet.F0,
				packet.F1,
				gain,
				packet.Noise,
				packet.PulsePeriodMs,
				packet.PulseWidthMs,
				pan
			));
		}
	}

	public void EnableWind(EffectSpec effect, EventPacket evt, float busGain, float masterGain)
	{
		var pan = ComputePan(effect, evt.AzimuthDeg, evt.DirectionBand);
		var gain = evt.Intensity * effect.Gain * busGain * masterGain;
		_wind = new WindState(true, gain, pan, effect.Bus);
	}

	public void DisableWind() => _wind = _wind with { Enabled = false };

	public void RenderInterleaved(float[] outputInterleaved)
	{
		Array.Clear(outputInterleaved, 0, outputInterleaved.Length);
		var frames = outputInterleaved.Length / _cfg.Channels;
		var sampleRate = _cfg.SampleRate;

		List<Voice>? snapshot = null;
		lock (_lock)
		{
			if (_voices.Count > 0)
			{
				snapshot = _voices.ToList();
			}
		}

		var wind = _wind;
		var telemetry = _telemetry;
		var windPhase = 0.0f;
		var baseWindHz = 22.0f + Math.Clamp(telemetry.Speed * 0.6f, 0f, 28f);
		var windNoise = Math.Clamp(telemetry.Speed / 30f, 0f, 1f);
		var windGain = wind.Enabled ? wind.Gain * Math.Clamp(telemetry.Speed / 18f, 0.05f, 1.0f) : 0f;

		for (var i = 0; i < frames; i++)
		{
			var sampleIndex = _sampleCursor + i;
			float l = 0f;
			float r = 0f;

			if (snapshot is not null)
			{
				for (var v = 0; v < snapshot.Count; v++)
				{
					var voice = snapshot[v];
					if (!voice.TrySample(sampleIndex, sampleRate, _rng, out var vl, out var vr))
					{
						continue;
					}
					l += vl;
					r += vr;
				}
			}

			if (windGain > 0f)
			{
				var w = WindSample(baseWindHz, ref windPhase, sampleRate, windNoise);
				var (wl, wr) = ApplyPan(w * windGain, wind.Pan);
				l += wl;
				r += wr;
			}

			l = Math.Clamp(l, -1f, 1f);
			r = Math.Clamp(r, -1f, 1f);
			var baseIdx = i * _cfg.Channels;
			if (_cfg.Channels == 2)
			{
				outputInterleaved[baseIdx] = l;
				outputInterleaved[baseIdx + 1] = r;
			}
			else
			{
				outputInterleaved[baseIdx] = 0.5f * (l + r);
			}
		}

		_sampleCursor += frames;
		CleanupVoices();
	}

	private void CleanupVoices()
	{
		lock (_lock)
		{
			if (_voices.Count == 0) return;
			var now = _sampleCursor;
			_voices.RemoveAll(v => v.IsFinished(now));
		}
	}

	private static float WindSample(float hz, ref float phase, int sampleRate, float noiseMix)
	{
		phase += (float)(2.0 * Math.PI * hz / sampleRate);
		if (phase > 2.0f * MathF.PI) phase -= 2.0f * MathF.PI;
		var sine = MathF.Sin(phase);
		var noise = (float)(Random.Shared.NextDouble() * 2.0 - 1.0);
		return (1f - noiseMix) * sine + noiseMix * noise;
	}

	private static float ComputePan(EffectSpec effect, float? azimuthDeg, string? directionBand)
	{
		if (!effect.Routing.Equals("leftRightFromAzimuth", StringComparison.OrdinalIgnoreCase))
		{
			return 0f;
		}

		var az = azimuthDeg;
		if (az is null && !string.IsNullOrWhiteSpace(directionBand))
		{
			az = directionBand.Trim().ToLowerInvariant() switch
			{
				"front" => 0f,
				"right" => 90f,
				"rear" or "back" => 180f,
				"left" => -90f,
				_ => null
			};
		}

		if (az is null) return 0f;
		var rad = (float)(az.Value * (Math.PI / 180.0));
		var pan = MathF.Sin(rad);
		var shaped = MathF.CopySign(MathF.Pow(MathF.Abs(pan), PanGamma), pan);
		return Math.Clamp(shaped, -1f, 1f);
	}

	internal static (float L, float R) ApplyPan(float sample, float pan)
	{
		pan = Math.Clamp(pan, -1f, 1f);
		var angle = (pan + 1f) * (MathF.PI / 4f);
		var l = MathF.Cos(angle) * sample;
		var r = MathF.Sin(angle) * sample;
		return (l, r);
	}

	private readonly record struct WindState(bool Enabled, float Gain, float Pan, string Bus);

	private readonly record struct Voice(
		long StartSample,
		int DurationSamples,
		float F0,
		float F1,
		float Gain,
		float Noise,
		int PulsePeriodMs,
		int PulseWidthMs,
		float Pan)
	{
		private const int FadeMs = 5;
		private readonly float _phase = 0f;

		public bool IsFinished(long nowSample) => nowSample > StartSample + DurationSamples + 1;

		public bool TrySample(long sampleIndex, int sampleRate, Random rng, out float l, out float r)
		{
			l = 0f;
			r = 0f;
			if (sampleIndex < StartSample) return false;
			var rel = sampleIndex - StartSample;
			if (rel >= DurationSamples) return false;

			var t = (float)rel / sampleRate;
			var dur = (float)DurationSamples / sampleRate;
			var u = dur <= 0f ? 1f : Math.Clamp(t / dur, 0f, 1f);
			var hz = F0 + (F1 - F0) * u;

			// naive per-sample oscillator (phase derived from time for determinism)
			var phase = (float)(2.0 * Math.PI * hz * t);
			var sine = MathF.Sin(phase);
			var noise = (float)(rng.NextDouble() * 2.0 - 1.0);
			var sig = (1f - Math.Clamp(Noise, 0f, 1f)) * sine + Math.Clamp(Noise, 0f, 1f) * noise;

			var env = Envelope(rel, DurationSamples, sampleRate);
			var amp = Gain * env;
			if (PulsePeriodMs > 0 && PulseWidthMs > 0)
			{
				var ms = t * 1000f;
				var p = ms % PulsePeriodMs;
				if (p > PulseWidthMs) amp = 0f;
			}

			var (pl, pr) = ApplyPan(sig * amp, Pan);
			l = pl;
			r = pr;
			return true;
		}

		private static float Envelope(long relSample, int durSamples, int sampleRate)
		{
			var fadeSamples = (int)((FadeMs / 1000.0) * sampleRate);
			if (fadeSamples <= 1) return 1f;
			if (relSample < fadeSamples) return (float)relSample / fadeSamples;
			var tail = durSamples - relSample;
			if (tail < fadeSamples) return Math.Clamp((float)tail / fadeSamples, 0f, 1f);
			return 1f;
		}
	}
}
