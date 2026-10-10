namespace MiniMes.Simulator;

/// <summary>
/// 배속 시계. speed가 60이면 시뮬레이션 1분이 현실 1초다 (사이클타임 12분 → 12초마다 메시지).
/// 설비 코드는 "몇 분 기다린다"만 알고, 실제로 몇 초를 기다릴지는 이 클래스가 정한다.
/// </summary>
public sealed class SimulationClock(double speed)
{
    public double Speed { get; } = speed;

    public Task WaitSimulatedMinutes(double minutes, CancellationToken ct)
    {
        var realSeconds = minutes * 60 / Speed;
        return Task.Delay(TimeSpan.FromSeconds(realSeconds), ct);
    }
}
