using Microsoft.Data.Sqlite;

namespace MiniMes.Simulator;

/// <summary>버퍼에 저장된 메시지 한 건. Id가 작을수록 먼저 들어온 메시지다.</summary>
public record BufferedMessage(long Id, string Topic, string Payload);

/// <summary>
/// 보낼 메시지를 잠시 보관하는 로컬 저장소 (SQLite 파일 하나).
/// 모든 메시지는 일단 여기에 들어가고, MqttPublisher가 오래된 것부터 꺼내 보낸 뒤 지운다.
/// 파일에 저장하므로 브로커가 꺼져 있거나 시뮬레이터를 껐다 켜도 메시지가 사라지지 않는다.
/// </summary>
public sealed class MessageBuffer : IDisposable
{
    private readonly SqliteConnection _connection;

    // 설비 5대가 동시에 Add를 부르고 보내는 쪽도 동시에 읽는다. 연결 하나를 여러 스레드가 동시에 쓰면 안 되므로 한 번에 하나씩.
    // 여기 메서드에는 await가 없어서 SemaphoreSlim 대신 더 간단한 lock을 쓴다.
    private readonly object _lock = new();   // lock에 쓰는 열쇠 객체 (.NET 9부터는 전용 Lock 타입이 있지만 이 프로젝트는 .NET 8)

    public MessageBuffer(string filePath)
    {
        _connection = new SqliteConnection($"Data Source={filePath}");
        _connection.Open();
        Execute("""
            CREATE TABLE IF NOT EXISTS outbox (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                topic      TEXT NOT NULL,
                payload    TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """);
        // 공장 시계를 기억하는 칸. 다시 실행해도 시각이 뒤로 가지 않게 마지막 시뮬레이션 시각을 남긴다.
        Execute("""
            CREATE TABLE IF NOT EXISTS sim_clock (
                id        INTEGER PRIMARY KEY CHECK (id = 1),   -- 행은 항상 하나
                last_unix INTEGER NOT NULL                      -- 마지막 공장 시각 (유닉스 시간, 밀리초)
            )
            """);
    }

    public void Add(string topic, string payload)
    {
        lock (_lock)
        {
            using var command = _connection.CreateCommand();
            command.CommandText = "INSERT INTO outbox (topic, payload, created_at) VALUES ($topic, $payload, $createdAt)";
            command.Parameters.AddWithValue("$topic", topic);       // 파라미터 바인딩: SQL 인젝션을 막는다 (MyBatis의 #{}와 같은 역할)
            command.Parameters.AddWithValue("$payload", payload);
            command.Parameters.AddWithValue("$createdAt", DateTimeOffset.Now.ToString("O"));
            command.ExecuteNonQuery();
        }
    }

    /// <summary>가장 오래된 메시지 한 건. 비어 있으면 null. 꺼내기만 하고 지우지는 않는다 (보내기에 성공한 뒤에 Remove).</summary>
    public BufferedMessage? PeekOldest()
    {
        lock (_lock)
        {
            using var command = _connection.CreateCommand();
            command.CommandText = "SELECT id, topic, payload FROM outbox ORDER BY id LIMIT 1";
            using var reader = command.ExecuteReader();
            return reader.Read()
                ? new BufferedMessage(reader.GetInt64(0), reader.GetString(1), reader.GetString(2))
                : null;
        }
    }

    public void Remove(long id)
    {
        lock (_lock)
        {
            using var command = _connection.CreateCommand();
            command.CommandText = "DELETE FROM outbox WHERE id = $id";
            command.Parameters.AddWithValue("$id", id);
            command.ExecuteNonQuery();
        }
    }

    /// <summary>공장 시각을 기록한다. 이미 더 늦은 시각이 있으면 그대로 둔다 (설비 5대가 조금씩 다른 시각을 보내므로).</summary>
    public void SaveSimTime(DateTimeOffset at)
    {
        lock (_lock)
        {
            using var command = _connection.CreateCommand();
            command.CommandText = """
                INSERT INTO sim_clock (id, last_unix) VALUES (1, $unix)
                ON CONFLICT (id) DO UPDATE SET last_unix = MAX(last_unix, excluded.last_unix)
                """;
            command.Parameters.AddWithValue("$unix", at.ToUnixTimeMilliseconds());
            command.ExecuteNonQuery();
        }
    }

    /// <summary>지난 실행의 마지막 공장 시각. 처음 실행이면 null.</summary>
    public DateTimeOffset? LoadSimTime()
    {
        lock (_lock)
        {
            using var command = _connection.CreateCommand();
            command.CommandText = "SELECT last_unix FROM sim_clock WHERE id = 1";
            return command.ExecuteScalar() is long unix
                ? DateTimeOffset.FromUnixTimeMilliseconds(unix).ToLocalTime()
                : null;
        }
    }

    public int Count()
    {
        lock (_lock)
        {
            using var command = _connection.CreateCommand();
            command.CommandText = "SELECT COUNT(*) FROM outbox";
            return Convert.ToInt32(command.ExecuteScalar());
        }
    }

    private void Execute(string sql)
    {
        using var command = _connection.CreateCommand();
        command.CommandText = sql;
        command.ExecuteNonQuery();
    }

    public void Dispose() => _connection.Dispose();
}
