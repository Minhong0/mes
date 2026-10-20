namespace MiniMes.Simulator;

/// <summary>
/// 설비 한 대의 동작. 가동하며 사이클마다 1개씩 가공하고, 부트캠프 실제 정지 기록을 따라 멈췄다가 다시 가동한다.
///
///   RUN ──(정지 시각 도달)──▶ DOWN 또는 SETUP ──(정지 시간 경과)──▶ RUN
///
/// - 언제 멈출지: 이 설비의 실제 정지 빈도(예: CNC03은 하루 2.9회)로 다음 정지 시각을 정한다.
/// - 왜, 얼마나: 이 설비의 과거 정지 한 건을 무작위로 골라 사유와 시간을 그대로 쓴다.
/// - 고장 징후: 다음 정지가 스핀들 이상·베어링 마모면, 정지 전 일정 시간 동안 진동과 온도가 서서히 오른다.
/// </summary>
public sealed class SimulatedEquipment
{
    // 진동이 먼저 나빠지는 고장 사유. 이 고장이 다가오면 진동·온도가 오른다.
    private static readonly HashSet<string> VibrationFailures = ["BRK_SPINDLE", "BRK_BEARING"];

    // 고장 징후가 나타나기 시작하는 시점: 정지 몇 분 전부터 (시뮬레이션 분)
    private const double WarningWindowMinutes = 180;

    // 정지 중에도 이 간격(시뮬레이션 분)마다 상태를 보낸다 (heartbeat). 실제 설비도 멈춰 있는 동안 상태를 계속 알린다.
    // 서버는 30분 넘게 소식이 없으면 설비가 꺼졌거나 통신이 끊긴 것으로 본다. 가동 중에는 사이클(최대 약 21분)마다 보내므로 괜찮다.
    private const double HeartbeatMinutes = 10;

    private readonly EquipmentSpec _spec;
    private readonly IReadOnlyList<DowntimeEvent> _history;
    private readonly double _meanMinutesBetweenStops;
    private readonly MqttPublisher _publisher;
    private readonly SimulationClock _clock;
    private readonly Random _random;
    private readonly string _runId;

    private string _status = "RUN";
    private int _cycleCount;
    private int _sequence;

    // 이 설비의 시뮬레이션 시각(분). 사건(사이클 끝, 정지 시작·끝)이 일어나기로 "정해진" 시각이다.
    // 실제 처리가 조금 늦어도 이 값은 계획대로 흘러가므로, 시간이 밀리지 않는다.
    private double _simNow;

    private DowntimeEvent? _nextStop;       // 다음에 일어날 정지 (미리 정해 둔다. 그래야 고장 징후를 앞서 보여 줄 수 있다)
    private double _nextStopAtMinutes;      // 다음 정지 시각 (시뮬레이션 시작 후 분)
    private DowntimeEvent? _currentStop;    // 지금 진행 중인 정지 (가동 중이면 null)

    public SimulatedEquipment(EquipmentSpec spec, DowntimeHistory history, MqttPublisher publisher,
                              SimulationClock clock, string runId, int seed)
    {
        _spec = spec;
        _history = history.For(spec.EquipCode);
        _publisher = publisher;
        _clock = clock;
        _runId = runId;
        _random = new Random(seed);   // 설비마다 다른 seed: 5대가 똑같이 움직이지 않게

        // 평균 정지 간격 = 관측 기간에 실제로 가동한 시간 ÷ 정지 횟수
        // 정지 중인 시간은 빼야 한다. 다음 정지는 이번 정지가 끝난 뒤부터 세기 때문이다.
        // 예: CNC03 = (23일 × 960분 − 정지 3,388분) ÷ 66건 ≈ 283분 가동마다 한 번
        var observedMinutes = (double)history.ObservedDays * spec.PlannedMinutesPerDay;
        var downtimeMinutes = _history.Sum(e => e.DurationMinutes);
        _meanMinutesBetweenStops = _history.Count == 0
            ? double.PositiveInfinity
            : (observedMinutes - downtimeMinutes) / _history.Count;
    }

    /// <summary>Ctrl+C(취소)가 올 때까지 가동과 정지를 반복한다.</summary>
    public async Task RunAsync(CancellationToken ct)
    {
        _simNow = _clock.NowMinutes;
        ScheduleNextStop();
        Publish();   // 시작 상태(RUN)를 먼저 알린다

        while (!ct.IsCancellationRequested)
        {
            // 1) 한 사이클 가공. 이론 사이클타임보다 0~15% 느리게 걸린다 (OEE 성능효율 < 100%).
            var cycleMinutes = _spec.IdealCycleMinutes * (1 + _random.NextDouble() * 0.15);
            _simNow += cycleMinutes;
            await _clock.WaitUntil(_simNow, ct);
            ct.ThrowIfCancellationRequested();   // 이미 지난 시각이면 기다리지 않으므로, 취소 여부를 직접 확인한다
            _cycleCount++;
            Publish();

            // 2) 정지 시각이 지났으면 멈춘다. 가공 도중이 아니라 사이클이 끝난 뒤에 멈추는 것으로 단순화했다.
            if (_nextStop != null && _simNow >= _nextStopAtMinutes)
            {
                await StopAsync(_nextStop, ct);
                ScheduleNextStop();
            }
        }
    }

    /// <summary>
    /// 다음 정지를 미리 정한다.
    /// - 시각: 평균 간격을 가진 지수분포에서 뽑는다.
    /// - 내용: 과거 정지 기록 중 하나를 고른다. 자주 있었던 사유가 자주 뽑히므로 사유별 빈도도 자연스럽게 재현된다.
    /// </summary>
    private void ScheduleNextStop()
    {
        if (_history.Count == 0)
        {
            _nextStop = null;
            return;
        }
        var gap = -Math.Log(1 - _random.NextDouble()) * _meanMinutesBetweenStops;
        _nextStopAtMinutes = _simNow + gap;
        _nextStop = _history[_random.Next(_history.Count)];
    }

    private async Task StopAsync(DowntimeEvent stop, CancellationToken ct)
    {
        _currentStop = stop;
        _status = stop.Category == "SETUP" ? "SETUP" : "DOWN";   // 셋업은 계획된 정지라 고장과 구분한다
        Publish();

        var endAt = _simNow + stop.DurationMinutes;
        while (_simNow + HeartbeatMinutes < endAt)
        {
            _simNow += HeartbeatMinutes;
            await _clock.WaitUntil(_simNow, ct);
            ct.ThrowIfCancellationRequested();
            Publish();   // 같은 상태(DOWN/SETUP)와 사유를 다시 보낸다. 서버는 상태가 그대로라 이력을 바꾸지 않는다
        }

        _simNow = endAt;
        await _clock.WaitUntil(_simNow, ct);
        ct.ThrowIfCancellationRequested();

        _currentStop = null;
        _status = "RUN";
        Publish();
    }

    /// <summary>
    /// 고장 징후의 정도 (0 = 정상, 1 = 고장 직전).
    /// 다음 정지가 진동성 고장이고 남은 시간이 경고 구간(180분) 안이면, 가까워질수록 커진다.
    /// </summary>
    private double DegradationLevel()
    {
        if (_nextStop == null || !VibrationFailures.Contains(_nextStop.Reason))
        {
            return 0;
        }
        var minutesLeft = _nextStopAtMinutes - _simNow;
        return Math.Clamp(1 - minutesLeft / WarningWindowMinutes, 0, 1);
    }

    private void Publish()
    {
        _sequence++;
        var (vibration, temp) = ReadSensors();
        var collectedAt = _clock.At(_simNow);   // 현실 시각이 아니라 공장 시계: 사건이 일어나기로 정해진 시뮬레이션 시각
        var message = new EquipmentMessage(
            MessageId: $"{_spec.EquipCode}-{_runId}-{_sequence:D6}",
            EquipCode: _spec.EquipCode,
            CollectedAt: collectedAt,
            CycleCount: _cycleCount,
            Status: _status,
            SpindleVibration: vibration,
            SpindleTemp: temp,
            DowntimeCategory: _currentStop?.Category,
            DowntimeReason: _currentStop?.Reason);

        _publisher.Enqueue(message);   // 버퍼에 적기만 한다. 실제 전송은 MqttPublisher가 따로 한다

        var reason = _currentStop == null ? "" : $"  사유 {_currentStop.Reason} {_currentStop.DurationMinutes}분";
        Console.WriteLine($"{collectedAt:MM-dd HH:mm} [{_spec.EquipCode}] {_status,-5} 누적 {_cycleCount,3}개  진동 {vibration,4} mm/s  온도 {temp} °C{reason}");
    }

    /// <summary>
    /// 스핀들 진동(mm/s)과 온도(°C). 실제 센서 값이 아닌 가상 데이터다.
    /// - 가동 중: 진동 1.0~1.5, 온도 35~38. 고장이 다가오면 진동은 최대 +3.0, 온도는 최대 +10까지 오른다.
    /// - 정지 중: 스핀들이 멈춰 진동 0~0.1, 온도 28~30.
    /// </summary>
    private (double Vibration, double Temp) ReadSensors()
    {
        if (_status != "RUN")
        {
            return (Math.Round(_random.NextDouble() * 0.1, 2), Math.Round(28 + _random.NextDouble() * 2, 1));
        }
        var level = DegradationLevel();
        var vibration = 1.0 + _random.NextDouble() * 0.5 + 3.0 * level * level;   // 제곱: 처음엔 천천히, 고장 직전에 급하게 오른다
        var temp = 35 + _random.NextDouble() * 3 + 10 * level;
        return (Math.Round(vibration, 2), Math.Round(temp, 1));
    }
}
