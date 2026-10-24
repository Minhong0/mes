using System.Collections.ObjectModel;
using System.Globalization;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using MiniMes.Pop.Api;

namespace MiniMes.Pop.ViewModels;

/// <summary>
/// 검사 탭: 고른 공정에서 만든 LOT을 골라, 그 공정의 검사 항목을 시료 여러 개로 재고 등록한다.
///
/// 칸에 값을 넣으면 하한·상한으로 바로 OK/NG를 보여 주지만, 최종 판정은 서버가 한다.
/// 하나라도 NG면 서버가 그 LOT과 하위 LOT을 보류한다. LOT 하나는 공정마다 한 번만 검사하고,
/// 같은 값을 다시 보내면 서버가 처음 결과를 돌려주므로 응답이 없으면 그냥 다시 누르면 된다.
/// </summary>
public partial class InspectionViewModel : ObservableObject
{
    public const int DefaultSamples = 3;
    public const int MaxSamples = 10;

    private readonly IMesApi _api;
    private readonly Worker _worker;
    private readonly Action<string, bool> _showMessage;   // (메시지, 오류인가): 작업 화면 아래 메시지 줄에 띄운다
    private int _loadVersion;

    public InspectionViewModel(IMesApi api, Worker worker, Action<string, bool> showMessage)
    {
        _api = api;
        _worker = worker;
        _showMessage = showMessage;
    }

    /// <summary>고른 공정에서 만든 LOT (최근 실적의 것부터)</summary>
    public ObservableCollection<string> Lots { get; } = [];

    public ObservableCollection<InspectionRow> Rows { get; } = [];

    /// <summary>표 머리의 시료 번호 (1..SampleCount)</summary>
    public ObservableCollection<int> SampleNumbers { get; } = [];

    [ObservableProperty]
    private string? _selectedLot;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsDone), nameof(HasSpecs), nameof(NoSpecs), nameof(ResultText))]
    [NotifyCanExecuteChangedFor(nameof(SaveCommand), nameof(AddSampleCommand), nameof(RemoveSampleCommand))]
    private InspectionSheet? _sheet;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(AddSampleCommand), nameof(RemoveSampleCommand))]
    private int _sampleCount = DefaultSamples;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SaveCommand))]
    private bool _isBusy;

    /// <summary>이미 검사한 LOT이면 값을 보여 주기만 한다</summary>
    public bool IsDone => Sheet?.Judgement != null;

    public bool HasSpecs => Sheet?.Items.Count > 0;

    /// <summary>LOT을 골랐는데 그 공정에 검사 항목이 없다 (세척, 포장)</summary>
    public bool NoSpecs => Sheet != null && Sheet.Items.Count == 0;

    /// <summary>예: "합격 · 10-22 14:05 W1001", "불합격 · 보류 중". 검사 전이면 null</summary>
    public string? ResultText => Sheet?.Judgement switch
    {
        null => null,
        "OK" => $"합격 · {Sheet.MeasuredAt:MM-dd HH:mm} {Sheet.WorkerId}",
        _ => $"불합격 · {Sheet.MeasuredAt:MM-dd HH:mm} {Sheet.WorkerId}" + (Sheet.Status == "HOLD" ? " · 보류 중" : ""),
    };

    /// <summary>공정의 실적 목록이 바뀌면 그 실적들이 만든 LOT을 다시 채운다. 보던 LOT은 그대로 둔다.</summary>
    public void ShowResults(IEnumerable<WorkResult> results)
    {
        var keep = SelectedLot;
        Lots.Clear();
        foreach (var lotNo in results.Where(r => !r.Cancelled).OrderByDescending(r => r.ResultId)
                     .SelectMany(r => r.OutputLotNos ?? []))
        {
            Lots.Add(lotNo);
        }
        SelectedLot = Lots.Contains(keep ?? "") ? keep : Lots.FirstOrDefault();
    }

    partial void OnSelectedLotChanged(string? value) => _ = LoadAsync();

    [RelayCommand]
    public async Task LoadAsync()
    {
        var version = ++_loadVersion;   // LOT을 빨리 바꾸면 조회가 겹친다. 마지막 조회 결과만 넣는다
        var lotNo = SelectedLot;
        if (lotNo == null)
        {
            Show(null);
            return;
        }
        try
        {
            var sheet = await _api.GetInspectionAsync(lotNo);
            if (version == _loadVersion)
            {
                Show(sheet);
            }
        }
        catch (Exception e) when (e is MesApiException or MesConnectionException)
        {
            _showMessage(e.Message, true);
        }
    }

    private bool CanAddSample() => Sheet != null && !IsDone && SampleCount < MaxSamples;

    [RelayCommand(CanExecute = nameof(CanAddSample))]
    private void AddSample() => SetSampleCount(SampleCount + 1);

    private bool CanRemoveSample() => Sheet != null && !IsDone && SampleCount > 1;

    [RelayCommand(CanExecute = nameof(CanRemoveSample))]
    private void RemoveSample() => SetSampleCount(SampleCount - 1);

    private bool CanSave() => !IsBusy && HasSpecs && !IsDone && Rows.All(r => r.Cells.All(c => c.Value != null));

    [RelayCommand(CanExecute = nameof(CanSave))]
    private async Task SaveAsync()
    {
        var lotNo = Sheet!.LotNo;
        var request = new InspectRequest(_worker.WorkerId, Rows
            .SelectMany(r => r.Cells.Select(c => new Measurement(r.Item.InspectCode, c.SampleNo, c.Value!.Value)))
            .ToList());
        IsBusy = true;
        try
        {
            var saved = await _api.InspectAsync(lotNo, request);
            Show(saved);
            if (saved.Judgement == "OK")
            {
                _showMessage($"{lotNo} 검사 합격 (시료 {saved.SampleCount}개)", false);
            }
            else
            {
                var held = saved.HeldLotNos.Count > 0 ? $" 보류한 LOT {saved.HeldLotNos.Count}개: {string.Join(", ", saved.HeldLotNos)}" : "";
                _showMessage($"{lotNo} 검사 불합격. 이 LOT은 품질 담당이 해제할 때까지 쓸 수 없습니다.{held}", true);
            }
        }
        catch (MesConnectionException e)
        {
            _showMessage($"{e.Message} [검사 등록]을 다시 누르세요. 같은 값이라 두 번 저장되지 않습니다.", true);
        }
        catch (MesApiException e)
        {
            _showMessage(e.Message, true);
        }
        finally
        {
            IsBusy = false;
        }
    }

    /// <summary>검사표를 화면에 옮긴다. 검사한 LOT이면 측정값을, 아니면 빈 칸을 시료 수만큼</summary>
    private void Show(InspectionSheet? sheet)
    {
        Sheet = sheet;
        Rows.Clear();
        foreach (var item in sheet?.Items ?? [])
        {
            Rows.Add(new InspectionRow(item, OnCellChanged));
        }
        SetSampleCount(IsDone ? sheet!.SampleCount : DefaultSamples);
        if (IsDone)
        {
            foreach (var row in Rows)
            {
                row.ShowMeasured();
            }
        }
    }

    private void SetSampleCount(int count)
    {
        count = Math.Clamp(count, 1, MaxSamples);
        SampleCount = count;
        SampleNumbers.Clear();
        for (var n = 1; n <= count; n++)
        {
            SampleNumbers.Add(n);
        }
        foreach (var row in Rows)
        {
            row.Resize(count, !IsDone);
        }
        SaveCommand.NotifyCanExecuteChanged();
    }

    private void OnCellChanged() => SaveCommand.NotifyCanExecuteChanged();
}

/// <summary>검사 항목 한 줄: 항목 이름, 기준, 시료 칸들</summary>
public sealed class InspectionRow(InspectionItem item, Action changed)
{
    public InspectionItem Item => item;

    /// <summary>예: "37.95 ~ 38.05 mm", 상한만 있으면 "≤ 3.2 um"</summary>
    public string RangeText => (item.Lsl, item.Usl) switch
    {
        ({ } lsl, { } usl) => $"{lsl:0.####} ~ {usl:0.####} {item.Unit}",
        (null, { } usl) => $"≤ {usl:0.####} {item.Unit}",
        ({ } lsl, null) => $"≥ {lsl:0.####} {item.Unit}",
        _ => item.Unit,
    };

    public ObservableCollection<SampleCell> Cells { get; } = [];

    /// <summary>시료 수를 바꾼다. 이미 적은 값은 남긴다</summary>
    public void Resize(int count, bool editable)
    {
        while (Cells.Count > count)
        {
            Cells.RemoveAt(Cells.Count - 1);
        }
        while (Cells.Count < count)
        {
            Cells.Add(new SampleCell(Cells.Count + 1, item, editable, changed));
        }
    }

    /// <summary>서버에 저장된 측정값을 칸에 채운다 (읽기 전용)</summary>
    public void ShowMeasured()
    {
        foreach (var s in item.Samples)
        {
            var cell = Cells.FirstOrDefault(c => c.SampleNo == s.SampleNo);
            cell?.Show(s.Value, s.Judgement);
        }
    }
}

/// <summary>
/// 측정값 칸 하나. 값을 넣으면 바로 OK/NG를 보여 준다 (화면 안내용, 최종 판정은 서버).
/// 숫자가 아니면 Value가 null이라 등록할 수 없다.
/// </summary>
public sealed partial class SampleCell(int sampleNo, InspectionItem item, bool editable, Action changed) : ObservableObject
{
    public int SampleNo => sampleNo;

    public bool IsReadOnly => !editable;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(Value), nameof(Judgement))]
    private string _text = "";

    private string? _savedJudgement;

    public decimal? Value => decimal.TryParse(Text.Trim(), NumberStyles.Float, CultureInfo.InvariantCulture, out var v) ? v : null;

    /// <summary>빈 칸이면 null, 숫자가 아니면 "?", 아니면 "OK" / "NG"</summary>
    public string? Judgement
    {
        get
        {
            if (_savedJudgement != null)
            {
                return _savedJudgement;
            }
            if (string.IsNullOrWhiteSpace(Text))
            {
                return null;
            }
            if (Value is not { } v)
            {
                return "?";
            }
            return (item.Lsl == null || v >= item.Lsl) && (item.Usl == null || v <= item.Usl) ? "OK" : "NG";
        }
    }

    partial void OnTextChanged(string value) => changed();

    public void Show(decimal value, string judgement)
    {
        _savedJudgement = judgement;
        Text = value.ToString("0.####", CultureInfo.InvariantCulture);
    }
}
