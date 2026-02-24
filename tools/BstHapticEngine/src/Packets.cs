using System.Text.Json;

namespace BstHapticEngine;

public readonly record struct HapticPacket(
	string Key,
	float F0,
	float F1,
	int Ms,
	float Gain,
	float Noise,
	int PulsePeriodMs,
	int PulseWidthMs,
	int Priority,
	int DelayMs,
	float? AzimuthDeg,
	string? DirectionBand
)
{
	public static bool TryParse(JsonElement root, out HapticPacket packet)
	{
		packet = default;
		if (!root.TryGetProperty("key", out var keyEl) || keyEl.ValueKind != JsonValueKind.String)
		{
			return false;
		}

		var key = keyEl.GetString() ?? "";
		var f0 = root.TryGetSingle("f0") ?? 30f;
		var f1 = root.TryGetSingle("f1") ?? f0;
		var ms = root.TryGetInt("ms") ?? 60;
		var gain = root.TryGetSingle("gain") ?? 1f;
		var noise = root.TryGetSingle("noise") ?? 0f;
		var pulsePeriod = root.TryGetInt("pulsePeriodMs") ?? 0;
		var pulseWidth = root.TryGetInt("pulseWidthMs") ?? 0;
		var priority = root.TryGetInt("priority") ?? 0;
		var delay = root.TryGetInt("delayMs") ?? 0;
		var az = root.TryGetSingle("azimuthDeg");
		var band = root.TryGetString("directionBand");

		packet = new HapticPacket(
			key,
			f0,
			f1,
			ms,
			gain,
			noise,
			pulsePeriod,
			pulseWidth,
			priority,
			delay,
			az,
			band
		);
		return true;
	}
}

public readonly record struct TelemetryPacket(float Speed, float Accel, bool Elytra)
{
	public static bool TryParse(JsonElement root, out TelemetryPacket packet)
	{
		packet = default;
		var speed = root.TryGetSingle("speed") ?? 0f;
		var accel = root.TryGetSingle("accel") ?? 0f;
		var elytra = root.TryGetBool("elytra") ?? false;
		packet = new TelemetryPacket(speed, accel, elytra);
		return true;
	}
}

public readonly record struct EventPacket(string Id, string Kind, float Intensity, float? AzimuthDeg, string? DirectionBand)
{
	public static bool TryParse(JsonElement root, out EventPacket packet)
	{
		packet = default;
		var id = root.TryGetString("id") ?? "";
		var kind = root.TryGetString("kind") ?? "";
		var intensity = root.TryGetSingle("intensity") ?? 0.5f;
		var az = root.TryGetSingle("azimuthDeg");
		var band = root.TryGetString("directionBand");
		packet = new EventPacket(id, kind, intensity, az, band);
		return true;
	}
}

internal static class JsonElExt
{
	public static float? TryGetSingle(this JsonElement root, string propName)
	{
		if (!root.TryGetProperty(propName, out var el)) return null;
		try
		{
			return el.ValueKind switch
			{
				JsonValueKind.Number => el.GetSingle(),
				JsonValueKind.String when float.TryParse(el.GetString(), out var v) => v,
				_ => null
			};
		}
		catch
		{
			return null;
		}
	}

	public static int? TryGetInt(this JsonElement root, string propName)
	{
		if (!root.TryGetProperty(propName, out var el)) return null;
		try
		{
			return el.ValueKind switch
			{
				JsonValueKind.Number => el.GetInt32(),
				JsonValueKind.String when int.TryParse(el.GetString(), out var v) => v,
				_ => null
			};
		}
		catch
		{
			return null;
		}
	}

	public static bool? TryGetBool(this JsonElement root, string propName)
	{
		if (!root.TryGetProperty(propName, out var el)) return null;
		try
		{
			return el.ValueKind switch
			{
				JsonValueKind.True => true,
				JsonValueKind.False => false,
				JsonValueKind.String when bool.TryParse(el.GetString(), out var v) => v,
				_ => null
			};
		}
		catch
		{
			return null;
		}
	}

	public static string? TryGetString(this JsonElement root, string propName)
	{
		if (!root.TryGetProperty(propName, out var el)) return null;
		if (el.ValueKind != JsonValueKind.String) return null;
		return el.GetString();
	}
}
