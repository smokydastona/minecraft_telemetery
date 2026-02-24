using System.Net.WebSockets;
using System.Text;
using System.Text.Json;

namespace BstHapticEngine;

internal static class Program
{
	private static async Task<int> Main(string[] args)
	{
		var configPath = "engine.json";
		var mappingsPath = "mappings.json";
		var listDevices = false;
		var click = false;

		for (var i = 0; i < args.Length; i++)
		{
			var a = args[i];
			switch (a)
			{
				case "--config" when i + 1 < args.Length:
					configPath = args[++i];
					break;
				case "--map" when i + 1 < args.Length:
					mappingsPath = args[++i];
					break;
				case "--list-devices":
					listDevices = true;
					break;
				case "--click":
					click = true;
					break;
				case "--help":
				case "-h":
				case "/?":
					PrintUsage();
					return 0;
				default:
					Console.Error.WriteLine($"Unknown arg: {a}");
					PrintUsage();
					return 2;
			}
		}

		var config = EngineConfig.Load(configPath);

		if (listDevices)
		{
			AudioDeviceUtil.PrintRenderDevices();
			return 0;
		}

		var mappings = MappingConfig.Load(mappingsPath);
		using var output = AudioOutput.Create(config);
		using var engine = new HapticEngine(config, mappings, output);

		if (click)
		{
			engine.TriggerClick();
			await Task.Delay(250);
			return 0;
		}

		using var cts = new CancellationTokenSource();
		Console.CancelKeyPress += (_, e) =>
		{
			e.Cancel = true;
			cts.Cancel();
		};

		await RunWebSocketLoop(config.WsUrl, engine, cts.Token);
		return 0;
	}

	private static void PrintUsage()
	{
		Console.WriteLine("BST Haptic Engine");
		Console.WriteLine("Usage:");
		Console.WriteLine("  dotnet run -- --config engine.json --map mappings.json");
		Console.WriteLine("Options:");
		Console.WriteLine("  --config <path>       Engine config JSON (default: engine.json)");
		Console.WriteLine("  --map <path>          Mapping rules JSON (default: mappings.json)");
		Console.WriteLine("  --list-devices        Print Windows render devices");
		Console.WriteLine("  --click               Play a calibration click and exit");
	}

	private static async Task RunWebSocketLoop(string wsUrl, HapticEngine engine, CancellationToken token)
	{
		var backoffMs = 500;
		while (!token.IsCancellationRequested)
		{
			try
			{
				using var ws = new ClientWebSocket();
				await ws.ConnectAsync(new Uri(wsUrl), token);
				Console.WriteLine($"Connected: {wsUrl}");

				backoffMs = 500;
				await ReceiveLoop(ws, engine, token);
			}
			catch (OperationCanceledException)
			{
				return;
			}
			catch (Exception ex)
			{
				Console.Error.WriteLine($"WebSocket error: {ex.Message}");
			}

			try
			{
				await Task.Delay(backoffMs, token);
			}
			catch (OperationCanceledException)
			{
				return;
			}

			backoffMs = Math.Min(backoffMs * 2, 5000);
		}
	}

	private static async Task ReceiveLoop(ClientWebSocket ws, HapticEngine engine, CancellationToken token)
	{
		var buffer = new byte[16 * 1024];
		var sb = new StringBuilder(16 * 1024);

		while (!token.IsCancellationRequested && ws.State == WebSocketState.Open)
		{
			sb.Clear();
			WebSocketReceiveResult result;
			do
			{
				result = await ws.ReceiveAsync(buffer, token);
				if (result.MessageType == WebSocketMessageType.Close)
				{
					await ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "bye", token);
					return;
				}

				if (result.Count > 0)
				{
					sb.Append(Encoding.UTF8.GetString(buffer, 0, result.Count));
				}
			}
			while (!result.EndOfMessage);

			if (sb.Length == 0)
			{
				continue;
			}

			try
			{
				using var doc = JsonDocument.Parse(sb.ToString());
				engine.HandlePacket(doc.RootElement);
			}
			catch (Exception ex)
			{
				Console.Error.WriteLine($"Packet parse error: {ex.Message}");
			}
		}
	}
}
