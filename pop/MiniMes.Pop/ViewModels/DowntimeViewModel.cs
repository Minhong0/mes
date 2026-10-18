using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using MiniMes.Pop.Api;

namespace MiniMes.Pop.ViewModels;

/// <summary>
/// 설비 정지 탭: 고른 설비의 최근 정지를 보여 주고, 작업자가 정지 사유(대분류·소분류)를 확정한다.
///
/// 설비가 보낸 사유는 처음 판단일 뿐이라, 실제 사유는 현장 작업자가 정한다.
/// 확정한 사유는 서버의 같은 정지 기록에 들어가 모니터링 화면과 정지 사유 파레토에 바로 쓰인다.
/// 사유 확정은 같은 내용을 다시 보내도 결과가 같아서(PUT), 응답이 없으면 그냥 다시 누르면 된다.
/// </summary>
public partial class DowntimeViewModel : ObservableObject
{
    public const int RecentCount = 10;

    private readonly IMesApi _api;
    private readonly Worker _worker;
    private readonly Action<string, bool> _showMessage;   // (메시지, 오류인가): 작업 화면 아래 메시지 줄에 띄운다
    private IReadOnlyList<CommonCode> _allSubs = [];
    private int _loadVersion;

    public DowntimeViewModel(IMesApi api, Worker worker, Action<string, bool> showMessage)
    {
        _api = api;
        _worker = worker;
        _showMessage = showMessage;
    }

    public ObservableCollection<DowntimeRow> Downtimes { get; } = [];

    public ObservableCollection<CommonCode> Categories { get; } = [];

    /// <summary>고른 대분류에 속한 소분류만</summary>
    public ObservableCollection<CommonCode> SubReasons { get; } = [];

    [ObservableProperty]
    private string? _equipCode;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SaveReasonCommand))]
    private DowntimeRow? _selectedDowntime;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SaveReasonCommand))]
    private CommonCode? _selectedCategory;

    [ObservableProperty]
    private CommonCode? _selectedSubReason;

    [ObservableProperty]
    private string? _note;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(SaveReasonCommand))]
    private bool _isBusy;

    /// <summary>작업자가 아직 사유를 확정하지 않은 정지 수 (탭 머리에 보인다)</summary>
    public int UnconfirmedCount => Downtimes.Count(d => !d.IsConfirmed);

    public string TabHeader => UnconfirmedCount == 0 ? "설비 정지" : $"설비 정지 (미확인 {UnconfirmedCount})";

    /// <summary>정지 사유 코드(대분류 DT_CAT, 소분류 DT_SUB)를 읽는다. 작업 화면이 처음 뜰 때 한 번.</summary>
    public async Task InitializeAsync()
    {
        var categories = await _api.GetCodesAsync("DT_CAT");
        _allSubs = await _api.GetCodesAsync("DT_SUB");
        Categories.Clear();
        foreach (var c in categories)
        {
            Categories.Add(c);
        }
    }

    /// <summary>설비를 바꾸면 그 설비의 정지 목록을 새로 읽는다.</summary>
    public Task ShowEquipmentAsync(string? equipCode)
    {
        EquipCode = equipCode;
        return LoadAsync();
    }

    [RelayCommand]
    public async Task LoadAsync()
    {
        var version = ++_loadVersion;   // 설비를 빨리 바꾸면 조회가 겹친다. 마지막 조회 결과만 넣는다
        var before = SelectedDowntime?.Source.DowntimeId;
        if (EquipCode == null)
        {
            Downtimes.Clear();
            NotifyCountChanged();
            return;
        }
        try
        {
            var list = await _api.GetDowntimesAsync(EquipCode, RecentCount);
            if (version != _loadVersion)
            {
                return;
            }
            Downtimes.Clear();
            foreach (var d in list)
            {
                Downtimes.Add(ToRow(d));
            }
            SelectedDowntime = Downtimes.FirstOrDefault(d => d.Source.DowntimeId == before);
            NotifyCountChanged();
        }
        catch (Exception e) when (e is MesApiException or MesConnectionException)
        {
            _showMessage(e.Message, true);
        }
    }

    partial void OnSelectedDowntimeChanged(DowntimeRow? value)
    {
        // 고른 정지의 지금 사유를 입력 칸에 채운다. 바꿀 것만 고치면 된다
        SelectedCategory = Categories.FirstOrDefault(c => c.Code == value?.Source.DtCat);
        SelectedSubReason = SubReasons.FirstOrDefault(s => s.Code == value?.Source.DtSub);
        Note = value?.Source.Note;
    }

    partial void OnSelectedCategoryChanged(CommonCode? value)
    {
        var keep = SelectedSubReason?.Code;
        SubReasons.Clear();
        foreach (var s in _allSubs.Where(s => s.ParentCode == value?.Code))
        {
            SubReasons.Add(s);
        }
        SelectedSubReason = SubReasons.FirstOrDefault(s => s.Code == keep);   // 다른 대분류의 소분류면 비운다
    }

    private bool CanSaveReason() => !IsBusy && SelectedDowntime != null && SelectedCategory != null;

    [RelayCommand(CanExecute = nameof(CanSaveReason))]
    private async Task SaveReasonAsync()
    {
        var row = SelectedDowntime!;
        var request = new ReasonRequest(_worker.WorkerId, SelectedCategory!.Code, SelectedSubReason?.Code,
            string.IsNullOrWhiteSpace(Note) ? null : Note.Trim());
        IsBusy = true;
        try
        {
            var saved = await _api.ConfirmDowntimeReasonAsync(row.Source.DowntimeId, request);
            var index = Downtimes.IndexOf(row);
            var updated = ToRow(saved);
            if (index >= 0)
            {
                Downtimes[index] = updated;
            }
            SelectedDowntime = updated;
            NotifyCountChanged();
            _showMessage($"{saved.EquipCode} {saved.StartedAt:MM-dd HH:mm} 정지 사유를 '{updated.ReasonText}'(으)로 확정했습니다.", false);
        }
        catch (MesConnectionException e)
        {
            _showMessage($"{e.Message} [사유 저장]을 다시 누르세요. 같은 내용이라 다시 보내도 괜찮습니다.", true);
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

    private DowntimeRow ToRow(Downtime d)
    {
        var category = Categories.FirstOrDefault(c => c.Code == d.DtCat)?.CodeName ?? d.DtCat;
        var sub = d.DtSub == null ? null : _allSubs.FirstOrDefault(s => s.Code == d.DtSub)?.CodeName ?? d.DtSub;
        return new DowntimeRow(d, sub == null ? category : $"{category} · {sub}");
    }

    private void NotifyCountChanged()
    {
        OnPropertyChanged(nameof(UnconfirmedCount));
        OnPropertyChanged(nameof(TabHeader));
    }
}

/// <summary>정지 목록의 한 줄. ReasonText: 사유 이름 (예: "설비고장 · 스핀들 이상")</summary>
public sealed record DowntimeRow(Downtime Source, string ReasonText)
{
    public bool IsOpen => Source.EndedAt == null;

    public bool IsConfirmed => Source.ReasonAt != null;

    public string DurationText => IsOpen ? "정지 중" : $"{Source.DurationMin}분";
}
