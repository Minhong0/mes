using System.Text.Json;
using System.Text.Json.Serialization;
using MQTTnet;
using MQTTnet.Protocol;

namespace MiniMes.Simulator;

/// <summary>
/// 메시지를 브로커로 보내는 담당 (하나뿐).
///
///   설비 ──Enqueue──▶ MessageBuffer(SQLite) ──RunAsync가 오래된 것부터 발행──▶ 브로커
///
/// - 설비는 Enqueue로 버퍼에 적기만 하므로, 통신이 끊겨도 기다리지 않고 계속 생산한다.
/// - RunAsync는 연결이 끊기면 간격을 늘려 가며 다시 연결하고, 연결되면 쌓인 메시지를 순서대로 보낸다.
/// - 브로커가 받았다고 확인한(QoS 1) 메시지만 버퍼에서 지운다. 보내다 끊기면 다음에 다시 보낸다.
///   그래서 같은 메시지가 두 번 갈 수는 있어도 사라지지는 않는다. 중복은 서버가 messageId로 거른다.
/// </summary>
public sealed class MqttPublisher : IAsyncDisposable
{
    // JSON 필드 이름을 서버(Java) 관례인 camelCase로 맞추고, 값이 null인 필드(가동 중의 정지 사유)는 JSON에서 뺀다.
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    };

    // 재연결 대기: 1초에서 시작해 실패할 때마다 두 배, 최대 30초 (지수 백오프)
    private static readonly TimeSpan FirstRetryDelay = TimeSpan.FromSeconds(1);
    private static readonly TimeSpan MaxRetryDelay = TimeSpan.FromSeconds(30);

    private readonly string _host;
    private readonly int _port;
    private readonly MessageBuffer _buffer;
    private readonly IMqttClient _client;

    // "새 메시지가 들어왔다"는 신호. 보내는 쪽은 버퍼가 비면 이 신호를 기다리며 쉰다.
    private readonly SemaphoreSlim _newMessage = new(0);

    public MqttPublisher(string host, int port, MessageBuffer buffer)
    {
        _host = host;
        _port = port;
        _buffer = buffer;
        _client = new MqttClientFactory().CreateMqttClient();
    }

    /// <summary>설비가 부른다. 버퍼에 적고 바로 돌아온다 (네트워크를 기다리지 않음).</summary>
    public void Enqueue(EquipmentMessage message)
    {
        _buffer.Add($"minimes/equipment/{message.EquipCode}/data", JsonSerializer.Serialize(message, JsonOptions));
        _buffer.SaveSimTime(message.CollectedAt);
        _newMessage.Release();   // 쉬고 있는 보내는 쪽을 깨운다
    }

    /// <summary>Ctrl+C가 올 때까지: 연결 확인 → 쌓인 메시지 보내기 → 새 메시지 기다리기를 반복한다.</summary>
    public async Task RunAsync(CancellationToken ct)
    {
        var retryDelay = FirstRetryDelay;
        while (!ct.IsCancellationRequested)
        {
            try
            {
                if (!_client.IsConnected)
                {
                    await ConnectAsync(ct);
                    retryDelay = FirstRetryDelay;   // 연결에 성공했으니 대기 시간을 처음으로 되돌린다
                    Console.WriteLine($"[통신] 브로커 연결됨 ({_host}:{_port}). 보낼 메시지 {_buffer.Count()}건");
                }

                await SendBufferedAsync(ct);

                // 새 메시지가 들어오면 바로, 아니면 1초 뒤에 다시 확인한다 (그 사이 연결이 끊겼는지도 점검)
                await _newMessage.WaitAsync(TimeSpan.FromSeconds(1), ct);
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested)
            {
                throw;   // Ctrl+C: 정상 종료이므로 위(Program)로 넘긴다
            }
            catch (Exception e)   // 연결 실패, 보내는 중 끊김 등 통신 오류
            {
                Console.WriteLine($"[통신] 실패: {e.Message.Split('\n')[0]} → {retryDelay.TotalSeconds}초 후 재시도 (쌓인 메시지 {_buffer.Count()}건)");
                await Task.Delay(retryDelay, ct);
                retryDelay = TimeSpan.FromTicks(Math.Min(retryDelay.Ticks * 2, MaxRetryDelay.Ticks));
            }
        }
    }

    private async Task ConnectAsync(CancellationToken ct)
    {
        var options = new MqttClientOptionsBuilder()
            .WithTcpServer(_host, _port)
            .WithClientId($"minimes-simulator-{Environment.ProcessId}")   // 같은 ID로 두 개 접속하면 브로커가 먼저 접속한 쪽을 끊는다
            .WithTimeout(TimeSpan.FromSeconds(5))                          // 응답이 없으면 5초 뒤 실패로 보고 재시도
            .Build();
        await _client.ConnectAsync(options, ct);
    }

    /// <summary>버퍼가 빌 때까지 가장 오래된 메시지부터 보낸다. 실패하면 예외가 RunAsync로 올라가 재연결한다.</summary>
    private async Task SendBufferedAsync(CancellationToken ct)
    {
        while (_buffer.PeekOldest() is { } item)   // 패턴 매칭: null이 아니면 item에 담고 반복
        {
            var mqttMessage = new MqttApplicationMessageBuilder()
                .WithTopic(item.Topic)
                .WithPayload(item.Payload)
                .WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce)   // QoS 1: 브로커의 수신 확인을 받는다
                .Build();

            await _client.PublishAsync(mqttMessage, ct);   // 확인을 받아야 다음 줄로 넘어간다
            _buffer.Remove(item.Id);                       // 확인받은 메시지만 지운다
        }
    }

    public async ValueTask DisposeAsync()
    {
        if (_client.IsConnected)
        {
            await _client.DisconnectAsync();
        }
        _client.Dispose();
        _newMessage.Dispose();
    }
}
