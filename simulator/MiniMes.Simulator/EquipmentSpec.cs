using System.Globalization;

namespace MiniMes.Simulator;

/// <summary>
/// 시뮬레이션할 설비 한 대의 기준정보. data/master/equipment.csv에서 읽는다.
/// 서버(DB)와 같은 CSV를 쓰므로, 사이클타임을 바꾸면 서버와 시뮬레이터가 함께 바뀐다.
/// </summary>
/// <param name="PlannedMinutesPerDay">하루 계획 가동시간(분). 정지 빈도를 "하루 몇 번"에서 "몇 분에 한 번"으로 바꿀 때 쓴다.</param>
public record EquipmentSpec(string EquipCode, string EquipName, double IdealCycleMinutes, int PlannedMinutesPerDay)
{
    /// <summary>
    /// equipment.csv에서 자동 수집 대상(auto_collect = Y) 설비만 읽는다.
    /// 첫 줄은 헤더이고, 컬럼 순서가 바뀌어도 되도록 헤더 이름으로 위치를 찾는다.
    /// </summary>
    public static List<EquipmentSpec> LoadFromCsv(string path)
    {
        var lines = File.ReadAllLines(path);
        var header = lines[0].Split(',');
        int Col(string name) => Array.IndexOf(header, name);   // 헤더 이름 → 컬럼 번호

        int code = Col("equip_code"), name = Col("equip_name"), ct = Col("ideal_ct_min"),
            planned = Col("planned_min_per_day"), auto = Col("auto_collect");

        return lines.Skip(1)
            .Where(line => !string.IsNullOrWhiteSpace(line))
            .Select(line => line.Split(','))
            .Where(cols => cols[auto] == "Y")
            .Select(cols => new EquipmentSpec(
                cols[code],
                cols[name],
                double.Parse(cols[ct], CultureInfo.InvariantCulture),
                int.Parse(cols[planned], CultureInfo.InvariantCulture)))
            .ToList();
    }
}
