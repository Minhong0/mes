using System.Text.Json;
using System.Text.Json.Serialization;
using MQTTnet;
using MQTTnet.Protocol;

namespace MiniMes.Simulator;

/// <summary>
/// MQTT 연결 하나를 설비 5대가 함께 쓴다. 메시지를 JSON으로 바꿔 설비별 토픽으로 발행한다.
/// </summary>
public sealed class MqttPublisher : IAsyncDisposable
{
    // JSON 필드 이름을 서버(Java) 관례인 camelCase로 맞추고, 값이 null인 필드(가동 중의 정지 사유)는 JSON에서 뺀다.
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull
    };

    private readonly IMqttClient _client;

    // 설비 5대가 동시에 발행하므로, 한 번에 하나씩만 보내도록 줄을 세운다.
    private readonly SemaphoreSlim _sendLock = new(1, 1);

    private MqttPublisher(IMqttClient client)
    {
        _client = client;
    }

    /// <summary>브로커에 연결한 발행기를 만든다. 생성자는 await를 쓸 수 없어서 정적 메서드로 연결까지 한다.</summary>
    public static async Task<MqttPublisher> ConnectAsync(string host, int port, CancellationToken ct)
    {
        var client = new MqttClientFactory().CreateMqttClient();
        var options = new MqttClientOptionsBuilder()
            .WithTcpServer(host, port)
            .WithClientId($"minimes-simulator-{Environment.ProcessId}")   // 같은 ID로 두 개 접속하면 브로커가 먼저 접속한 쪽을 끊는다
            .Build();
        await client.ConnectAsync(options, ct);
        return new MqttPublisher(client);
    }

    public async Task PublishAsync(EquipmentMessage message, CancellationToken ct)
    {
        var mqttMessage = new MqttApplicationMessageBuilder()
            .WithTopic($"minimes/equipment/{message.EquipCode}/data")
            .WithPayload(JsonSerializer.Serialize(message, JsonOptions))
            .WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce)
            .Build();

        await _sendLock.WaitAsync(ct);
        try
        {
            await _client.PublishAsync(mqttMessage, ct);
        }
        finally
        {
            _sendLock.Release();   // 예외가 나도 반드시 줄을 풀어 준다. 안 풀면 다른 설비가 영원히 기다린다.
        }
    }

    public async ValueTask DisposeAsync()
    {
        if (_client.IsConnected)
        {
            await _client.DisconnectAsync();
        }
        _client.Dispose();
        _sendLock.Dispose();
    }
}
