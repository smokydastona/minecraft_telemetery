using System.Text.Json;

namespace BstHapticEngine;

public sealed class HapticEngine : IDisposable
{
	private readonly EngineConfig _cfg;
	private readonly MappingConfig _mappings;
	private readonly AudioOutput _output;
	private readonly Dictionary<string, float> _busGains;

	public HapticEngine(EngineConfig cfg, MappingConfig mappings, AudioOutput output)
	{
		_cfg = cfg;
		_mappings = mappings;
		_output = output;
		_busGains = cfg.Buses.ToDictionary(kvp => kvp.Key, kvp => kvp.Value.Gain, StringComparer.OrdinalIgnoreCase);
	}

	public void Dispose() => _output.Dispose();

	public void HandlePacket(JsonElement root)
	{
		var type = root.TryGetString("type") ?? "";
		switch (type)
		{
			case "telemetry":
				if (TelemetryPacket.TryParse(root, out var tel))
				{
					_output.Synth.SetTelemetry(tel);
				}
				break;
			case "haptic":
				HandleHaptic(root);
				break;
			case "event":
				HandleEvent(root);
				break;
			default:
				// ignore
				break;
		}
	}

	public void TriggerClick()
	{
		var packet = new HapticPacket(
			Key: "cal.click",
			F0: 60f,
			F1: 30f,
			Ms: 40,
			Gain: 0.8f,
			Noise: 0.15f,
			PulsePeriodMs: 0,
			PulseWidthMs: 0,
			Priority: 0,
			DelayMs: 0,
			AzimuthDeg: 0f,
			DirectionBand: "front");

		var effect = new EffectSpec { Mode = "usePacket", Bus = "impacts", Gain = 1.0f, Routing = "all" };
		var busGain = GetBusGain(effect.Bus);
		_output.Synth.TriggerVoice(effect, packet, busGain, _cfg.MasterGain, _cfg.Latency.DelayMs);
	}

	private void HandleHaptic(JsonElement root)
	{
		if (!HapticPacket.TryParse(root, out var packet)) return;
		var effect = FindEffectForHaptic(packet);
		if (!effect.Mode.Equals("usePacket", StringComparison.OrdinalIgnoreCase))
		{
			return;
		}

		var busGain = GetBusGain(effect.Bus);
		_output.Synth.TriggerVoice(effect, packet, busGain, _cfg.MasterGain, _cfg.Latency.DelayMs);
	}

	private void HandleEvent(JsonElement root)
	{
		if (!EventPacket.TryParse(root, out var evt)) return;
		var effect = FindEffectForEvent(evt);
		if (effect.Mode.Equals("telemetryWind", StringComparison.OrdinalIgnoreCase))
		{
			var busGain = GetBusGain(effect.Bus);
			_output.Synth.EnableWind(effect, evt, busGain, _cfg.MasterGain);
		}
	}

	private EffectSpec FindEffectForHaptic(HapticPacket packet)
	{
		foreach (var rule in _mappings.Rules)
		{
			if (!rule.When.Type.Equals("haptic", StringComparison.OrdinalIgnoreCase)) continue;
			if (!string.IsNullOrEmpty(rule.When.KeyPrefix) && !packet.Key.StartsWith(rule.When.KeyPrefix, StringComparison.OrdinalIgnoreCase))
			{
				continue;
			}
			return rule.Effect;
		}

		return _mappings.Fallback?.Haptic ?? new EffectSpec();
	}

	private EffectSpec FindEffectForEvent(EventPacket evt)
	{
		foreach (var rule in _mappings.Rules)
		{
			if (!rule.When.Type.Equals("event", StringComparison.OrdinalIgnoreCase)) continue;
			if (!string.IsNullOrEmpty(rule.When.Kind) && !evt.Kind.Equals(rule.When.Kind, StringComparison.OrdinalIgnoreCase))
			{
				continue;
			}
			return rule.Effect;
		}

		return new EffectSpec();
	}

	private float GetBusGain(string bus)
	{
		if (_busGains.TryGetValue(bus, out var g)) return g;
		return 1.0f;
	}
}
