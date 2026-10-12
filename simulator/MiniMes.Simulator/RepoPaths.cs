namespace MiniMes.Simulator;

/// <summary>
/// 저장소 안의 데이터 파일 찾기.
/// 실행 위치(bin 폴더 등)에서 위로 올라가며 파일을 찾으므로, dotnet run과 Visual Studio(F5) 어디서 실행해도 같은 파일을 쓴다.
/// </summary>
public static class RepoPaths
{
    /// <summary>예: Find("data", "master", "equipment.csv") → C:\...\MES\data\master\equipment.csv</summary>
    public static string Find(params string[] relativeParts)
    {
        var relative = Path.Combine(relativeParts);
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir != null)
        {
            var candidate = Path.Combine(dir.FullName, relative);
            if (File.Exists(candidate))
            {
                return candidate;
            }
            dir = dir.Parent;
        }
        throw new FileNotFoundException($"{relative}를 찾을 수 없습니다. 저장소 안에서 실행하세요.");
    }
}
