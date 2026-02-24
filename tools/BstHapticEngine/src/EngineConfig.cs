using System.Text.Json;
using System.Text.Json.Serialization;

namespace BstHapticEngine;

public sealed class EngineConfig
{
	[JsonPropertyName("wsUrl")] public string WsUrl { get; init; } = "ws://127.0.0.1:7117/";
	[JsonPropertyName("sampleRate")] public int SampleRate { get; init; } = 48000;
	[JsonPropertyName("channels")] public int Channels { get; init; } = 2;
	[JsonPropertyName("blockSize")] public int BlockSize { get; init; } = 512;
	[JsonPropertyName("targetBufferedBlocks")] public int TargetBufferedBlocks { get; init; } = 3;
	[JsonPropertyName("masterGain")] public float MasterGain { get; init; } = 1.0f;
	[JsonPropertyName("output")] public OutputConfig Output { get; init; } = new();
	[JsonPropertyName("latency")] public LatencyConfig Latency { get; init; } = new();
	[JsonPropertyName("buses")] public Dictionary<string, BusConfig> Buses { get; init; } = new(StringComparer.OrdinalIgnoreCase);

	public static EngineConfig Load(string path)
	{
		var json = File.ReadAllText(path);
		var cfg = JsonSerializer.Deserialize<EngineConfig>(json, JsonOpts.Options) ?? new EngineConfig();
		Validate(cfg);
		return cfg;
	}

	private static void Validate(EngineConfig cfg)
	{
		if (cfg.SampleRate <= 0) throw new ArgumentOutOfRangeException(nameof(SampleRate));
		if (cfg.Channels is < 1 or > 2) throw new ArgumentOutOfRangeException(nameof(Channels), "Only mono/stereo supported");
		if (cfg.BlockSize <= 0) throw new ArgumentOutOfRangeException(nameof(BlockSize));
		if (cfg.TargetBufferedBlocks <= 0) throw new ArgumentOutOfRangeException(nameof(TargetBufferedBlocks));
	}
}

public sealed class OutputConfig
{
	[JsonPropertyName("deviceNameContains")] public string DeviceNameContains { get; init; } = "";
	[JsonPropertyName("sharedMode")] public bool SharedMode { get; init; } = true;
	[JsonPropertyName("wasapiBufferMs")] public int WasapiBufferMs { get; init; } = 30;
}

public sealed class LatencyConfig
{
	[JsonPropertyName("delayMs")] public int DelayMs { get; init; } = 0;
}

public sealed class BusConfig
{
	[JsonPropertyName("gain")] public float Gain { get; init; } = 1.0f;
}

internal static class JsonOpts
{
	public static readonly JsonSerializerOptions Options = new()
	{
		PropertyNameCaseInsensitive = true,
		ReadCommentHandling = JsonCommentHandling.Skip,
		AllowTrailingCommas = true,
		NumberHandling = JsonNumberHandling.AllowReadingFromString
	};
}
