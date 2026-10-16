using System.Diagnostics;

namespace MiniMes.Simulator;

/// <summary>
/// 배속 시계. speed가 60이면 시뮬레이션 1분이 현실 1초다 (사이클타임 12분 → 12초마다 메시지).
/// 설비 코드는 "시뮬레이션 몇 분 시점까지 기다린다"만 알고, 현실 시간과의 변환은 이 클래스가 맡는다.
///
/// 공장 시계(StartAt + 시뮬레이션 경과 분)도 이 클래스가 만든다. 메시지의 측정 시각은 현실 시각이 아니라 공장 시계다.
/// 그래야 60배속에서 30분 정지가 서버에도 30분으로 기록되고, OEE가 현실과 같은 값으로 나온다.
/// </summary>
public sealed class SimulationClock(double speed, DateTimeOffset startAt)
{
    // 시뮬레이터가 시작된 뒤 흐른 현실 시간을 잰다. 시스템 시계를 바꿔도 영향을 받지 않는 정밀 타이머.
    private readonly Stopwatch _elapsed = Stopwatch.StartNew();

    public double Speed { get; } = speed;

    /// <summary>시뮬레이션 0분 시점의 공장 시각</summary>
    public DateTimeOffset StartAt { get; } = startAt;

    /// <summary>시뮬레이션 simMinutes 분 시점의 공장 시각. 예: StartAt 08:00, 90분 → 09:30</summary>
    public DateTimeOffset At(double simMinutes) => StartAt.AddMinutes(simMinutes);

    /// <summary>시뮬레이션 시작 후 지난 시간(시뮬레이션 분).</summary>
    public double NowMinutes => _elapsed.Elapsed.TotalSeconds * Speed / 60;

    /// <summary>
    /// 시뮬레이션 시각 targetMinutes가 될 때까지 기다린다. 이미 지났으면 바로 돌아온다.
    ///
    /// "12분 기다린다"(상대 시간)가 아니라 "120분 시점까지 기다린다"(절대 시각)로 기다리는 이유:
    /// 메시지 발행·화면 출력에 걸린 시간만큼 매 사이클 조금씩 늦어지는 것이 쌓이지 않게 하기 위해서다.
    /// 늦어진 만큼 다음 대기가 짧아져 자동으로 따라잡는다.
    /// </summary>
    public Task WaitUntil(double targetMinutes, CancellationToken ct)
    {
        var realSeconds = (targetMinutes - NowMinutes) * 60 / Speed;
        return realSeconds <= 0
            ? Task.CompletedTask   // 이미 지난 시각: 기다리지 않는다 (다만 취소 확인은 호출한 쪽 반복문이 한다)
            : Task.Delay(TimeSpan.FromSeconds(realSeconds), ct);
    }
}
