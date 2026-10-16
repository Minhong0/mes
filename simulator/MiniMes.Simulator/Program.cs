// 설비 시뮬레이터: 자동 수집 대상 설비(CNC01~05)가 동시에 가동·정지하며 데이터를 MQTT로 보낸다.
// 실행: dotnet run --project simulator/MiniMes.Simulator -- --speed 60   (먼저 docker compose up -d)
//   --speed: 배속. 60이면 시뮬레이션 1분 = 현실 1초 (기본 60)
// 종료: Ctrl+C

using System.Globalization;
using MiniMes.Simulator;

const string BrokerHost = "localhost";
const int BrokerPort = 1883;

var speed = ReadSpeed(args);

// Ctrl+C를 누르면 바로 죽지 않고, 모든 설비의 반복을 멈추고 연결을 정리한 뒤 끝나게 한다.
using var cts = new CancellationTokenSource();
Console.CancelKeyPress += (_, e) =>
{
    e.Cancel = true;
    cts.Cancel();
};

// 1) 기준정보 CSV에서 시뮬레이션할 설비 목록, 부트캠프 정지 기록 읽기
var specs = EquipmentSpec.LoadFromCsv(RepoPaths.Find("data", "master", "equipment.csv"));
var history = DowntimeHistory.LoadFromCsv(RepoPaths.Find("data", "profile", "downtime_events.csv"));
Console.WriteLine($"설비 {specs.Count}대: " + string.Join(", ", specs.Select(s => $"{s.EquipCode}({s.IdealCycleMinutes}분, 정지 기록 {history.For(s.EquipCode).Count}건)")));
Console.WriteLine($"배속 x{speed}: 시뮬레이션 1분 = 현실 {60 / speed:0.####}초");

// 2) 로컬 버퍼와 보내는 담당 준비. 연결은 보내는 담당이 알아서 하므로, 브로커가 꺼져 있어도 시뮬레이터는 시작한다.
var bufferPath = Path.Combine(AppContext.BaseDirectory, "outbox.db");
using var buffer = new MessageBuffer(bufferPath);
Console.WriteLine($"로컬 버퍼: {bufferPath} (이전 실행에서 못 보낸 메시지 {buffer.Count()}건)");
await using var publisher = new MqttPublisher(BrokerHost, BrokerPort, buffer);

// 3) 공장 시계의 시작 시각: 지금과 지난 실행의 마지막 공장 시각 중 늦은 쪽.
//    배속으로 돌리면 공장 시계가 현실보다 앞서 가므로, 다시 실행할 때 현실 시각으로 돌아가면 시간이 거꾸로 흐르게 된다.
var now = DateTimeOffset.Now;
var lastSimTime = buffer.LoadSimTime();
var startAt = lastSimTime > now ? lastSimTime.Value : now;   // null과 비교하면 false라서 처음 실행이면 now
startAt = startAt.AddTicks(-(startAt.Ticks % TimeSpan.TicksPerSecond));   // 초 아래는 버린다
Console.WriteLine($"공장 시계 시작: {startAt:yyyy-MM-dd HH:mm:ss}" + (lastSimTime > now ? " (지난 실행에서 이어서)" : ""));

// 4) 설비마다 작업(Task)을 하나씩 시작하고, 모두 끝날 때까지 기다린다
var clock = new SimulationClock(speed, startAt);
var runId = DateTimeOffset.Now.ToString("yyMMddHHmmss");   // 실행마다 다른 값: 재시작해도 messageId가 겹치지 않게
var tasks = specs
    .Select((spec, index) => new SimulatedEquipment(spec, history, publisher, clock, runId, seed: Environment.TickCount + index))
    .Select(equipment => equipment.RunAsync(cts.Token))
    .ToList();
tasks.Add(publisher.RunAsync(cts.Token));   // 보내는 담당도 설비와 나란히 돈다

try
{
    await Task.WhenAll(tasks);
}
catch (OperationCanceledException)
{
    // Ctrl+C로 취소된 경우. 정상 종료이므로 아무것도 하지 않는다.
}

Console.WriteLine($"시뮬레이터 종료 (못 보낸 메시지 {buffer.Count()}건은 다음 실행 때 보냄)");

static double ReadSpeed(string[] args)
{
    var index = Array.IndexOf(args, "--speed");
    // CultureInfo.InvariantCulture : 나라별 숫자 표기 차이를 무시하고 항상 .을 소수점으로 읽게 함 실무에서 중요해보임.
    if (index >= 0 && index + 1 < args.Length && double.TryParse(args[index + 1], NumberStyles.Float, CultureInfo.InvariantCulture, out var value) && value > 0)
    {
        return value;
    }
    return 60;
}
