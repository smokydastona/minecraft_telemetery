using NAudio.CoreAudioApi;
using NAudio.Wave;

namespace BstHapticEngine;

public sealed class AudioOutput : IDisposable
{
	private readonly WasapiOut _out;
	private readonly BufferedWaveProvider _buffer;
	private readonly int _blockBytes;
	private readonly int _targetBufferedBytes;
	private readonly CancellationTokenSource _cts = new();
	private readonly Task _renderTask;
	private readonly float[] _mix;
	private readonly byte[] _mixBytes;
	private readonly HapticSynth _synth;

	private AudioOutput(EngineConfig cfg, WasapiOut output, BufferedWaveProvider buffer, HapticSynth synth)
	{
		_out = output;
		_buffer = buffer;
		_synth = synth;

		_blockBytes = cfg.BlockSize * cfg.Channels * sizeof(float);
		_targetBufferedBytes = cfg.TargetBufferedBlocks * _blockBytes;

		_mix = new float[cfg.BlockSize * cfg.Channels];
		_mixBytes = new byte[_mix.Length * sizeof(float)];
		_renderTask = Task.Run(RenderLoop);
		_out.Play();
	}

	public HapticSynth Synth => _synth;

	public static AudioOutput Create(EngineConfig cfg)
	{
		MMDevice device = AudioDeviceUtil.FindRenderDeviceByNameContains(cfg.Output.DeviceNameContains);
		var shareMode = cfg.Output.SharedMode ? AudioClientShareMode.Shared : AudioClientShareMode.Exclusive;
		var output = new WasapiOut(device, shareMode, true, Math.Max(5, cfg.Output.WasapiBufferMs));
		var format = WaveFormat.CreateIeeeFloatWaveFormat(cfg.SampleRate, cfg.Channels);
		var buffer = new BufferedWaveProvider(format)
		{
			DiscardOnBufferOverflow = true
		};
		output.Init(buffer);

		Console.WriteLine($"Audio device: {device.FriendlyName}");
		Console.WriteLine($"Audio format: {cfg.SampleRate} Hz, {cfg.Channels} ch, float");

		var synth = new HapticSynth(cfg);
		return new AudioOutput(cfg, output, buffer, synth);
	}

	public void Dispose()
	{
		_cts.Cancel();
		try { _renderTask.Wait(250); } catch { }
		_out.Stop();
		_out.Dispose();
		_cts.Dispose();
	}

	private async Task RenderLoop()
	{
		var token = _cts.Token;
		try
		{
			while (!token.IsCancellationRequested)
			{
				if (_buffer.BufferedBytes < _targetBufferedBytes)
				{
					_synth.RenderInterleaved(_mix);
					Buffer.BlockCopy(_mix, 0, _mixBytes, 0, _mixBytes.Length);
					_buffer.AddSamples(_mixBytes, 0, _mixBytes.Length);
				}
				else
				{
					await Task.Delay(1, token);
				}
			}
		}
		catch (OperationCanceledException)
		{
			// ok
		}
		catch (Exception ex)
		{
			Console.Error.WriteLine($"Audio render error: {ex.Message}");
		}
	}
}
