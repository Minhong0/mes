using System.Globalization;

namespace MiniMes.Simulator;

/// <summary>과거 정지 한 건. 시뮬레이터는 이 중 하나를 무작위로 골라 그대로 재현한다.</summary>
/// <param name="Category">비가동 대분류 코드 (MATERIAL, TOOL, SETUP, BREAKDOWN, QUALITY)</param>
/// <param name="Reason">비가동 소분류 코드 (예: BRK_SPINDLE, TOOL_INSERT)</param>
public record DowntimeEvent(string Category, string Reason, int DurationMinutes);

/// <summary>
/// 부트캠프 2025년 10월 비가동 기록 (data/profile/downtime_events.csv, tools/import_excel.py가 생성).
/// 설비별로 "얼마나 자주"(빈도)와 "왜, 얼마나 오래"(사유와 시간)를 함께 제공한다.
/// </summary>
public sealed class DowntimeHistory
{
    private readonly Dictionary<string, List<DowntimeEvent>> _eventsByEquip;

    /// <summary>기록이 있는 날짜 수 (2025년 10월 근무일 23일). 빈도 계산의 분모.</summary>
    public int ObservedDays { get; }

    private DowntimeHistory(Dictionary<string, List<DowntimeEvent>> eventsByEquip, int observedDays)
    {
        _eventsByEquip = eventsByEquip;
        ObservedDays = observedDays;
    }

    /// <summary>설비의 과거 정지 기록. 기록이 없으면 빈 목록 (정지하지 않는 설비).</summary>
    public IReadOnlyList<DowntimeEvent> For(string equipCode) =>
        _eventsByEquip.TryGetValue(equipCode, out var events) ? events : [];

    public static DowntimeHistory LoadFromCsv(string path)
    {
        var lines = File.ReadAllLines(path);
        var header = lines[0].Split(',');
        int Col(string name) => Array.IndexOf(header, name);

        int date = Col("prod_date"), equip = Col("equip_code"), cat = Col("dt_cat"), sub = Col("dt_sub"), minutes = Col("duration_min");

        var rows = lines.Skip(1)
            .Where(line => !string.IsNullOrWhiteSpace(line))
            .Select(line => line.Split(','))
            .ToList();

        var eventsByEquip = rows
            .GroupBy(cols => cols[equip])
            .ToDictionary(
                group => group.Key,
                group => group.Select(cols => new DowntimeEvent(
                    cols[cat], cols[sub], int.Parse(cols[minutes], CultureInfo.InvariantCulture))).ToList());

        var observedDays = rows.Select(cols => cols[date]).Distinct().Count();
        return new DowntimeHistory(eventsByEquip, observedDays);
    }
}
