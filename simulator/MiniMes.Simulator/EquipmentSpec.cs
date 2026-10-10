using System.Globalization;

namespace MiniMes.Simulator;

/// <summary>
/// 시뮬레이션할 설비 한 대의 기준정보. data/master/equipment.csv에서 읽는다.
/// 서버(DB)와 같은 CSV를 쓰므로, 사이클타임을 바꾸면 서버와 시뮬레이터가 함께 바뀐다.
/// </summary>
public record EquipmentSpec(string EquipCode, string EquipName, double IdealCycleMinutes)
{
    /// <summary>
    /// equipment.csv에서 자동 수집 대상(auto_collect = Y) 설비만 읽는다.
    /// 첫 줄은 헤더이고, 컬럼 순서가 바뀌어도 되도록 헤더 이름으로 위치를 찾는다.
    /// </summary>
    public static List<EquipmentSpec> LoadFromCsv(string path)
    {
        var lines = File.ReadAllLines(path);
        var header = lines[0].Split(',');
        int Col(string name) => Array.IndexOf(header, name);   // 지역 함수: 헤더 이름 → 컬럼 번호

        int code = Col("equip_code"), name = Col("equip_name"), ct = Col("ideal_ct_min"), auto = Col("auto_collect");

        return lines.Skip(1)
            .Where(line => !string.IsNullOrWhiteSpace(line))
            .Select(line => line.Split(','))
            .Where(cols => cols[auto] == "Y")
            .Select(cols => new EquipmentSpec(cols[code], cols[name], double.Parse(cols[ct], CultureInfo.InvariantCulture)))
            .ToList();
    }

    /// <summary>
    /// 실행 위치(bin 폴더 등)에서 위로 올라가며 저장소 루트의 data/master/equipment.csv를 찾는다.
    /// dotnet run, Visual Studio(F5) 어디서 실행해도 같은 파일을 찾기 위해서다.
    /// </summary>
    public static string FindCsvPath()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir != null)
        {
            var candidate = Path.Combine(dir.FullName, "data", "master", "equipment.csv");
            if (File.Exists(candidate))
            {
                return candidate;
            }
            dir = dir.Parent;
        }
        throw new FileNotFoundException("data/master/equipment.csv를 찾을 수 없습니다. 저장소 안에서 실행하세요.");
    }
}
