using System.Text.Json;
using System.Text.Json.Serialization;

namespace BstHapticEngine;

public sealed class MappingConfig
{
	[JsonPropertyName("version")] public int Version { get; init; } = 1;
	[JsonPropertyName("rules")] public List<MappingRule> Rules { get; init; } = [];
	[JsonPropertyName("fallback")] public MappingFallback? Fallback { get; init; }

	public static MappingConfig Load(string path)
	{
		var json = File.ReadAllText(path);
		return JsonSerializer.Deserialize<MappingConfig>(json, JsonOpts.Options) ?? new MappingConfig();
	}
}

public sealed class MappingFallback
{
	[JsonPropertyName("haptic")] public EffectSpec? Haptic { get; init; }
}

public sealed class MappingRule
{
	[JsonPropertyName("when")] public WhenSpec When { get; init; } = new();
	[JsonPropertyName("effect")] public EffectSpec Effect { get; init; } = new();
}

public sealed class WhenSpec
{
	[JsonPropertyName("type")] public string Type { get; init; } = "";
	[JsonPropertyName("keyPrefix")] public string? KeyPrefix { get; init; }
	[JsonPropertyName("kind")] public string? Kind { get; init; }
}

public sealed class EffectSpec
{
	[JsonPropertyName("mode")] public string Mode { get; init; } = "usePacket";
	[JsonPropertyName("bus")] public string Bus { get; init; } = "impacts";
	[JsonPropertyName("gain")] public float Gain { get; init; } = 1.0f;
	[JsonPropertyName("routing")] public string Routing { get; init; } = "all";
}
