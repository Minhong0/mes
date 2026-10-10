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

// 1) 기준정보 CSV에서 시뮬레이션할 설비 목록 읽기
var csvPath = EquipmentSpec.FindCsvPath();
var specs = EquipmentSpec.LoadFromCsv(csvPath);
Console.WriteLine($"설비 {specs.Count}대: " + string.Join(", ", specs.Select(s => $"{s.EquipCode}({s.IdealCycleMinutes}분)")));
Console.WriteLine($"배속 x{speed}: 시뮬레이션 1분 = 현실 {60 / speed:0.##}초");

// 2) 브로커 연결 (설비 5대가 연결 하나를 함께 쓴다)
await using var publisher = await MqttPublisher.ConnectAsync(BrokerHost, BrokerPort, cts.Token);
Console.WriteLine($"브로커 연결: {BrokerHost}:{BrokerPort}");

// 3) 설비마다 작업(Task)을 하나씩 시작하고, 모두 끝날 때까지 기다린다
var clock = new SimulationClock(speed);
var runId = DateTimeOffset.Now.ToString("yyMMddHHmmss");   // 실행마다 다른 값: 재시작해도 messageId가 겹치지 않게
var tasks = specs
    .Select((spec, index) => new SimulatedEquipment(spec, publisher, clock, runId, seed: Environment.TickCount + index))
    .Select(equipment => equipment.RunAsync(cts.Token))
    .ToList();

try
{
    await Task.WhenAll(tasks);
}
catch (OperationCanceledException)
{
    // Ctrl+C로 취소된 경우. 정상 종료이므로 아무것도 하지 않는다.
}

Console.WriteLine("시뮬레이터 종료");

static double ReadSpeed(string[] args)
{
    var index = Array.IndexOf(args, "--speed");
    if (index >= 0 && index + 1 < args.Length && double.TryParse(args[index + 1], NumberStyles.Float, CultureInfo.InvariantCulture, out var value) && value > 0)
    {
        return value;
    }
    return 60;
}
