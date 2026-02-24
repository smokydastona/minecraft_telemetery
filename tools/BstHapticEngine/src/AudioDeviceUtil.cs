using NAudio.CoreAudioApi;

namespace BstHapticEngine;

internal static class AudioDeviceUtil
{
	public static void PrintRenderDevices()
	{
		using var enumerator = new MMDeviceEnumerator();
		var devices = enumerator.EnumerateAudioEndPoints(DataFlow.Render, DeviceState.Active);
		Console.WriteLine("Render devices:");
		for (var i = 0; i < devices.Count; i++)
		{
			var d = devices[i];
			Console.WriteLine($"  - {d.FriendlyName}");
		}
	}

	public static MMDevice FindRenderDeviceByNameContains(string nameContains)
	{
		using var enumerator = new MMDeviceEnumerator();
		var devices = enumerator.EnumerateAudioEndPoints(DataFlow.Render, DeviceState.Active);
		if (string.IsNullOrWhiteSpace(nameContains))
		{
			return enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
		}

		for (var i = 0; i < devices.Count; i++)
		{
			var d = devices[i];
			if (d.FriendlyName.Contains(nameContains, StringComparison.OrdinalIgnoreCase))
			{
				return d;
			}
		}

		return enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Multimedia);
	}
}
