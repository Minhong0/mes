namespace MiniMes.Simulator;

/// <summary>
/// 설비 한 대의 동작. 가동(RUN)하며 사이클마다 1개씩 가공하고, 가끔 정지(DOWN)했다가 다시 가동한다.
///
///   RUN ──(사이클 끝: 메시지 발행, 일정 확률로)──▶ DOWN ──(정지 시간 경과)──▶ RUN
///
/// 상태가 바뀔 때와 사이클이 끝날 때마다 메시지를 보낸다.
/// </summary>
public sealed class SimulatedEquipment
{
    // 사이클 한 번이 끝날 때 정지할 확률과 정지 시간(시뮬레이션 분). C단계에서 부트캠프 비가동 패턴으로 바꾼다.
    private const double StopProbability = 0.08;
    private const int MinStopMinutes = 5;
    private const int MaxStopMinutes = 30;

    private readonly EquipmentSpec _spec;
    private readonly MqttPublisher _publisher;
    private readonly SimulationClock _clock;
    private readonly Random _random;
    private readonly string _runId;

    private string _status = "RUN";
    private int _cycleCount;
    private int _sequence;

    public SimulatedEquipment(EquipmentSpec spec, MqttPublisher publisher, SimulationClock clock, string runId, int seed)
    {
        _spec = spec;
        _publisher = publisher;
        _clock = clock;
        _runId = runId;
        _random = new Random(seed);   // 설비마다 다른 seed: 5대가 똑같이 움직이지 않게
    }

    /// <summary>Ctrl+C(취소)가 올 때까지 가동과 정지를 반복한다.</summary>
    public async Task RunAsync(CancellationToken ct)
    {
        await PublishAsync(ct);   // 시작 상태(RUN)를 먼저 알린다

        while (!ct.IsCancellationRequested)
        {
            // 1) 한 사이클 가공. 실제 설비처럼 이론 사이클타임보다 0~15% 느리게 걸린다 (OEE 성능효율 < 100%).
            var cycleMinutes = _spec.IdealCycleMinutes * (1 + _random.NextDouble() * 0.15);
            await _clock.WaitSimulatedMinutes(cycleMinutes, ct);
            _cycleCount++;
            await PublishAsync(ct);

            // 2) 일정 확률로 정지했다가 다시 가동
            if (_random.NextDouble() < StopProbability)
            {
                var stopMinutes = _random.Next(MinStopMinutes, MaxStopMinutes + 1);
                await ChangeStatusAsync("DOWN", ct);
                await _clock.WaitSimulatedMinutes(stopMinutes, ct);
                await ChangeStatusAsync("RUN", ct);
            }
        }
    }

    private async Task ChangeStatusAsync(string status, CancellationToken ct)
    {
        _status = status;
        await PublishAsync(ct);
    }

    private async Task PublishAsync(CancellationToken ct)
    {
        _sequence++;
        var running = _status == "RUN";
        var message = new EquipmentMessage(
            MessageId: $"{_spec.EquipCode}-{_runId}-{_sequence:D6}",
            EquipCode: _spec.EquipCode,
            CollectedAt: DateTimeOffset.Now,
            CycleCount: _cycleCount,
            Status: _status,
            // 가동 중에는 스핀들이 돌아 진동과 온도가 높고, 정지하면 낮아진다.
            SpindleVibration: Math.Round(running ? 1.0 + _random.NextDouble() * 0.5 : _random.NextDouble() * 0.1, 2),
            SpindleTemp: Math.Round(running ? 35 + _random.NextDouble() * 3 : 28 + _random.NextDouble() * 2, 1));

        await _publisher.PublishAsync(message, ct);
        Console.WriteLine($"[{_spec.EquipCode}] {_status,-4} 누적 {_cycleCount,3}개  진동 {message.SpindleVibration,4} mm/s  온도 {message.SpindleTemp} °C");
    }
}
