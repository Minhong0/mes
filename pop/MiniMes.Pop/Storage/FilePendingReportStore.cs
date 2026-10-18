using System.IO;
using System.Text.Encodings.Web;
using System.Text.Json;

namespace MiniMes.Pop.Storage;

/// <summary>
/// 작업자마다 JSON 파일 하나: %LOCALAPPDATA%\MiniMes.Pop\pending\{사번}.json
/// 쓰는 도중에 꺼져도 반쯤 쓴 파일이 남지 않게, 임시 파일에 다 쓴 뒤 이름을 바꾼다.
/// </summary>
public sealed class FilePendingReportStore(string directory) : IPendingReportStore
{
    // 관리자가 파일을 열어 볼 수 있게 줄을 나누고, 한글을 \uXXXX로 바꾸지 않고 그대로 쓴다
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web)
    {
        WriteIndented = true,
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
    };

    public static string DefaultDirectory =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "MiniMes.Pop", "pending");

    public SavedReport? Load(string workerId)
    {
        var path = PathOf(workerId);
        if (!File.Exists(path))
        {
            return null;
        }
        try
        {
            return JsonSerializer.Deserialize<SavedReport>(File.ReadAllText(path), Json);
        }
        catch (JsonException)
        {
            // 읽을 수 없는 파일은 지우지 않고 옆으로 치워 둔다 (관리자가 내용을 확인할 수 있게)
            File.Move(path, path + ".broken", overwrite: true);
            return null;
        }
    }

    public void Save(SavedReport report)
    {
        Directory.CreateDirectory(directory);
        var path = PathOf(report.WorkerId);
        var temp = path + ".tmp";
        File.WriteAllText(temp, JsonSerializer.Serialize(report, Json));
        File.Move(temp, path, overwrite: true);
    }

    public void Clear(string workerId)
    {
        File.Delete(PathOf(workerId));   // 없는 파일이면 아무 일도 하지 않는다
    }

    private string PathOf(string workerId)
    {
        // 사번이 파일 이름이 된다. 경로를 벗어나는 문자가 들어오지 않게 막는다
        if (workerId.IndexOfAny(Path.GetInvalidFileNameChars()) >= 0 || workerId.Contains(".."))
        {
            throw new ArgumentException($"사번에 쓸 수 없는 문자가 있습니다: {workerId}");
        }
        return Path.Combine(directory, workerId + ".json");
    }
}
