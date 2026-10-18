using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using MiniMes.Pop.Api;
using MiniMes.Pop.Storage;

namespace MiniMes.Pop.ViewModels;

/// <summary>
/// 작업 화면: 작업장의 작업 목록 → 공정 시작 → 실적(양품·불량, 투입 LOT) 등록 → 공정 완료, 그리고 실적 취소.
///
/// 실적 전송이 통신 오류로 실패하면, 보낸 요청(같은 RequestId)을 PendingReport에 잡아 두고 입력을 잠근다.
/// 서버에 저장됐는지 알 수 없으므로 새로 입력받지 않고 "같은 요청"을 다시 보내게 한다.
/// 서버는 RequestId가 같으면 한 번만 저장하므로, 몇 번을 다시 보내도 실적이 두 번 생기지 않는다.
///
/// 요청은 보내기 전에 단말 파일(IPendingReportStore)에도 적어 둔다. 응답을 기다리다 POP가 꺼져도
/// 다음에 로그인하면 그 요청을 같은 RequestId로 다시 보낸다.
/// </summary>
public partial class WorkViewModel : ObservableObject
{
    public const string StatusWait = "WAIT";
    public const string StatusRun = "RUN";

    private readonly IMesApi _api;
    private readonly IDialogService _dialogs;
    private readonly IPendingReportStore _pendingStore;
    private readonly Action _logout;
    private IReadOnlyList<Equipment> _allEquipment = [];
    private bool _initialized;
    private bool _keepInputs;
    private int _resultsVersion;   // 목록을 새로 받는 동안 선택이 잠깐 바뀌어도 입력을 지우지 않는다
    private long _pendingWoOpId;   // 보내지 못한 실적이 어느 공정의 것인가

    public WorkViewModel(IMesApi api, IDialogService dialogs, IPendingReportStore pendingStore, Worker worker, Action logout)
    {
        _api = api;
        _dialogs = dialogs;
        _pendingStore = pendingStore;
        _logout = logout;
        Worker = worker;
        Downtime = new DowntimeViewModel(api, worker, (message, isError) =>
        {
            if (isError)
            {
                ShowError(message);
            }
            else
            {
                ShowInfo(message);
            }
        });
    }

    public Worker Worker { get; }

    /// <summary>설비 정지 탭</summary>
    public DowntimeViewModel Downtime { get; }

    public ObservableCollection<string> WorkCenters { get; } = [];

    public ObservableCollection<Equipment> EquipmentList { get; } = [];

    public ObservableCollection<OperationItem> Operations { get; } = [];

    public ObservableCollection<WorkResult> Results { get; } = [];

    public ObservableCollection<DefectEntry> Defects { get; } = [];

    /// <summary>고른 공정에 투입할 품목과 LOT. 실적 수량을 바꾸면 먼저 들어온 LOT부터 자동으로 고른다</summary>
    public ObservableCollection<InputLotGroup> InputGroups { get; } = [];

    [ObservableProperty]
    private string? _selectedWorkCenter;

    [ObservableProperty]
    private Equipment? _selectedEquipment;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(StartCommand), nameof(ReportCommand), nameof(CompleteCommand))]
    private OperationItem? _selectedOperation;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(TotalQty))]
    [NotifyCanExecuteChangedFor(nameof(ReportCommand))]
    private int _goodQty;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(HasPendingReport), nameof(IsInputEnabled), nameof(ReportButtonText))]
    [NotifyCanExecuteChangedFor(nameof(StartCommand), nameof(ReportCommand), nameof(CompleteCommand), nameof(DiscardPendingCommand))]
    private ReportRequest? _pendingReport;

    [ObservableProperty]
    [NotifyPropertyChangedFor(nameof(IsInputEnabled))]
    [NotifyCanExecuteChangedFor(nameof(StartCommand), nameof(ReportCommand), nameof(CompleteCommand))]
    private bool _isBusy;

    [ObservableProperty]
    private string? _message;

    [ObservableProperty]
    private bool _isError;

    public int DefectTotal => Defects.Sum(d => d.Qty);

    public int TotalQty => GoodQty + DefectTotal;

    public bool HasInputGroups => InputGroups.Count > 0;

    /// <summary>품목마다 필요한 만큼 LOT을 담았는가</summary>
    public bool InputsReady => InputGroups.All(g => g.IsSatisfied);

    public bool HasPendingReport => PendingReport != null;

    /// <summary>보내지 못한 실적이 있는 동안에는 다른 입력을 막는다.</summary>
    public bool IsInputEnabled => PendingReport == null && !IsBusy;

    public string ReportButtonText => PendingReport == null ? "실적 등록" : "다시 보내기";

    /// <summary>
    /// 설비·불량 유형·정지 사유를 읽고 소속 작업장의 작업 목록을 띄운다.
    /// 지난번에 결과를 모른 채 끝난 실적이 단말에 남아 있으면 그 작업장을 열고 다시 보낸다.
    /// </summary>
    public async Task InitializeAsync()
    {
        try
        {
            _allEquipment = await _api.GetEquipmentAsync();
            foreach (var code in await _api.GetCodesAsync("DEFECT_TYPE"))
            {
                Defects.Add(new DefectEntry(code.Code, code.CodeName, OnDefectChanged));
            }
            await Downtime.InitializeAsync();
        }
        catch (Exception e) when (e is MesApiException or MesConnectionException)
        {
            ShowError(e.Message);
            return;
        }

        foreach (var wc in _allEquipment.Select(e => e.WcCode).OfType<string>().Distinct().Order())
        {
            WorkCenters.Add(wc);
        }
        var saved = _pendingStore.Load(Worker.WorkerId);
        // 관리자는 소속 작업장이 없다. 보내지 못한 실적이 있으면 그 작업장부터
        SelectedWorkCenter = saved?.WcCode ?? Worker.WcCode ?? WorkCenters.FirstOrDefault();
        _initialized = true;
        await RefreshAsync();
        if (saved != null)
        {
            await ResendSavedAsync(saved);
        }
    }

    /// <summary>지난번에 보내지 못한 실적: 같은 요청을 그대로 다시 보낸다. 이미 저장됐어도 서버가 한 번만 남긴다.</summary>
    private async Task ResendSavedAsync(SavedReport saved)
    {
        _keepInputs = true;
        SelectedOperation = Operations.FirstOrDefault(o => o.WoOpId == saved.WoOpId) ?? SelectedOperation;
        _keepInputs = false;
        _pendingWoOpId = saved.WoOpId;
        PendingReport = saved.Request;
        GoodQty = saved.Request.GoodQty;   // 다시 보내는 동안 무엇을 보내는지 화면에 보인다
        foreach (var d in Defects)
        {
            d.Qty = saved.Request.Defects.FirstOrDefault(l => l.DefectType == d.Code)?.Qty ?? 0;
        }
        await SendReportAsync(saved.WoOpId, saved.WoNo, saved.ProcessName, saved.Request,
            $"지난번에 결과를 모른 채 끝난 실적({saved.WoNo} {saved.ProcessName}, {saved.SavedAt:MM-dd HH:mm})");
    }

    partial void OnSelectedWorkCenterChanged(string? value)
    {
        EquipmentList.Clear();
        foreach (var e in _allEquipment.Where(e => e.WcCode == value))
        {
            EquipmentList.Add(e);
        }
        SelectedEquipment = EquipmentList.FirstOrDefault();
        if (_initialized)
        {
            _ = RefreshAsync();
        }
    }

    partial void OnSelectedEquipmentChanged(Equipment? value)
    {
        if (_initialized)
        {
            _ = Downtime.ShowEquipmentAsync(value?.EquipCode);
        }
    }

    partial void OnSelectedOperationChanged(OperationItem? value)
    {
        if (!_keepInputs)
        {
            ResetInputs();   // 다른 작업을 고르면 입력을 비운다
        }
        _ = LoadResultsAsync();
    }

    [RelayCommand]
    public async Task RefreshAsync()
    {
        if (SelectedWorkCenter == null)
        {
            return;
        }
        var before = SelectedOperation;
        try
        {
            var operations = await _api.GetOpenOperationsAsync(SelectedWorkCenter);
            // 목록을 비우면 WPF ListBox가 선택을 null로 바꿔 알려 온다. 그 사이에 입력 중이던 수량이 지워지지 않게 막는다.
            _keepInputs = true;
            Operations.Clear();
            foreach (var op in operations)
            {
                Operations.Add(op);
            }
            // 목록을 새로 받아도 보던 작업을 계속 선택해 둔다 (완료해서 목록에서 빠졌으면 선택 해제)
            var reselected = Operations.FirstOrDefault(o => o.WoOpId == before?.WoOpId);
            _keepInputs = reselected != null;
            SelectedOperation = reselected;
            _keepInputs = false;
            if (reselected != null && reselected == before)
            {
                await LoadResultsAsync();   // 값이 그대로라 변경 알림이 오지 않은 경우에도 실적 목록은 새로 읽는다
            }
            await Downtime.ShowEquipmentAsync(SelectedEquipment?.EquipCode);
        }
        catch (Exception e) when (e is MesApiException or MesConnectionException)
        {
            ShowError(e.Message);
        }
    }

    private bool CanStart() => IsInputEnabled && SelectedOperation?.Status == StatusWait;

    [RelayCommand(CanExecute = nameof(CanStart))]
    private async Task StartAsync()
    {
        var op = SelectedOperation!;
        await RunAsync(async () =>
        {
            await _api.StartAsync(op.WoOpId, Worker.WorkerId);
            ShowInfo($"{op.WoNo} {op.ProcessName} 작업을 시작했습니다.");
            await RefreshAsync();
        });
    }

    // 보내지 못한 실적은 고른 작업과 상관없이 원래 공정으로 다시 보낸다
    private bool CanReport() =>
        !IsBusy && (PendingReport != null || (SelectedOperation?.Status == StatusRun && TotalQty > 0 && InputsReady));

    /// <summary>실적 등록. 보내지 못한 실적이 있으면 새로 만들지 않고 그 요청을 다시 보낸다.</summary>
    [RelayCommand(CanExecute = nameof(CanReport))]
    private async Task ReportAsync()
    {
        if (PendingReport != null)
        {
            var saved = _pendingStore.Load(Worker.WorkerId);
            await SendReportAsync(_pendingWoOpId, saved?.WoNo ?? "", saved?.ProcessName ?? "", PendingReport, "실적");
            return;
        }
        var op = SelectedOperation!;
        var request = new ReportRequest(
            Guid.NewGuid().ToString(),
            Worker.WorkerId,
            SelectedEquipment?.EquipCode,
            GoodQty,
            Defects.Where(d => d.Qty > 0).Select(d => new DefectLine(d.Code, d.Qty)).ToList(),
            InputGroups.SelectMany(g => g.ToInputs()).ToList());
        _pendingWoOpId = op.WoOpId;
        await SendReportAsync(op.WoOpId, op.WoNo, op.ProcessName, request, "실적");
    }

    /// <summary>
    /// 실적 요청을 보낸다. 보내기 전에 단말 파일에 적고, 결과가 확실해지면(저장됨 / 서버 거절) 지운다.
    /// 응답이 없으면 파일과 화면(PendingReport)에 남겨 같은 요청을 다시 보내게 한다.
    /// </summary>
    private async Task SendReportAsync(long woOpId, string woNo, string processName, ReportRequest request, string label)
    {
        IsBusy = true;
        try
        {
            _pendingStore.Save(new SavedReport(Worker.WorkerId, SelectedWorkCenter ?? "", woOpId, woNo, processName,
                request, DateTime.Now));
            var result = await _api.ReportAsync(woOpId, request);
            _pendingStore.Clear(Worker.WorkerId);
            PendingReport = null;
            ResetInputs();
            ShowInfo($"{label}을 등록했습니다: 양품 {result.GoodQty}, 불량 {result.DefectQty}"
                     + (result.OutputLotNo == null ? "" : $" → LOT {result.OutputLotNo}"));
            await RefreshAsync();
        }
        catch (MesConnectionException e)
        {
            PendingReport = request;   // 저장됐는지 모른다: 같은 요청을 다시 보내게 잡아 둔다
            ShowError($"{e.Message} [다시 보내기]를 누르세요. 같은 요청이라 두 번 저장되지 않습니다.");
        }
        catch (MesApiException e)
        {
            _pendingStore.Clear(Worker.WorkerId);
            PendingReport = null;      // 서버가 거절했다: 저장되지 않은 것이 확실하다
            ShowError(PendingLabel(label, e.Message));
        }
        finally
        {
            IsBusy = false;
        }
    }

    // 새로 입력한 실적이 거절되면 서버 메시지만, 지난번 실적이 거절되면 어떤 실적인지 함께 보여 준다
    private static string PendingLabel(string label, string message) =>
        label == "실적" ? message : $"{label}을 서버가 받지 않았습니다: {message}";

    private bool CanDiscardPending() => PendingReport != null;

    /// <summary>보내지 못한 실적을 버린다. 실제로는 저장됐을 수 있으므로 실적 목록을 다시 읽어 보여 준다.</summary>
    [RelayCommand(CanExecute = nameof(CanDiscardPending))]
    private async Task DiscardPendingAsync()
    {
        if (!_dialogs.Confirm("보내지 못한 실적을 버립니다.\n서버에 이미 저장됐을 수도 있으니, 버린 뒤 실적 목록을 꼭 확인하세요."))
        {
            return;
        }
        _pendingStore.Clear(Worker.WorkerId);
        PendingReport = null;
        ResetInputs();
        await RefreshAsync();
    }

    private bool CanComplete() => IsInputEnabled && SelectedOperation?.Status == StatusRun;

    [RelayCommand(CanExecute = nameof(CanComplete))]
    private async Task CompleteAsync()
    {
        var op = SelectedOperation!;
        if (!_dialogs.Confirm($"{op.WoNo} {op.ProcessName} 공정을 완료합니다.\n완료하면 이 공정에는 실적을 더 등록할 수 없습니다.\n(양품 {op.GoodQty} / 계획 {op.PlanQty})"))
        {
            return;
        }
        await RunAsync(async () =>
        {
            await _api.CompleteAsync(op.WoOpId, Worker.WorkerId);
            ShowInfo($"{op.WoNo} {op.ProcessName} 공정을 완료했습니다.");
            await RefreshAsync();
        });
    }

    [RelayCommand]
    private async Task CancelResultAsync(WorkResult result)
    {
        if (result.Cancelled)
        {
            return;
        }
        var reason = _dialogs.AskText("실적 취소", $"실적 #{result.ResultId} (양품 {result.GoodQty}, 불량 {result.DefectQty})을 취소하는 사유:");
        if (string.IsNullOrWhiteSpace(reason))
        {
            return;
        }
        await RunAsync(async () =>
        {
            await _api.CancelResultAsync(result.ResultId, Worker.WorkerId, reason.Trim());
            ShowInfo($"실적 #{result.ResultId}을 취소했습니다.");
            await RefreshAsync();
        });
    }

    /// <summary>양품 수량 버튼: +1, +5, +10, -1</summary>
    [RelayCommand]
    private void AddGood(string delta) => GoodQty = Math.Max(0, GoodQty + int.Parse(delta));

    [RelayCommand]
    private void Logout()
    {
        if (PendingReport != null &&
            !_dialogs.Confirm("보내지 못한 실적이 있습니다. 그래도 로그아웃할까요?\n이 단말에 저장해 두었다가 다음에 로그인하면 다시 보냅니다."))
        {
            return;
        }
        _logout();
    }

    /// <summary>고른 공정의 실적 목록과 투입할 LOT을 읽는다.</summary>
    private async Task LoadResultsAsync()
    {
        // 선택이 빨리 바뀌면 조회가 겹친다. 마지막에 시작한 조회의 결과만 화면에 넣는다.
        var version = ++_resultsVersion;
        var op = SelectedOperation;
        if (op == null)
        {
            Results.Clear();
            SetInputGroups([]);
            return;
        }
        try
        {
            var results = await _api.GetResultsAsync(op.WoOpId);
            var inputs = await _api.GetInputLotsAsync(op.WoOpId);
            if (version != _resultsVersion)
            {
                return;
            }
            Results.Clear();
            foreach (var r in results)
            {
                Results.Add(r);
            }
            SetInputGroups(inputs);
        }
        catch (Exception e) when (e is MesApiException or MesConnectionException)
        {
            ShowError(e.Message);
        }
    }

    /// <summary>바쁨 표시를 켜고 일을 한 뒤, 서버 오류는 메시지로 보여 준다.</summary>
    private async Task RunAsync(Func<Task> work)
    {
        IsBusy = true;
        try
        {
            await work();
        }
        catch (Exception e) when (e is MesApiException or MesConnectionException)
        {
            ShowError(e.Message);
        }
        finally
        {
            IsBusy = false;
        }
    }

    private void SetInputGroups(IReadOnlyList<InputLots> inputs)
    {
        InputGroups.Clear();
        foreach (var input in inputs)
        {
            var group = new InputLotGroup(input, () => ReportCommand.NotifyCanExecuteChanged());
            group.SetUnits(TotalQty);
            InputGroups.Add(group);
        }
        OnPropertyChanged(nameof(HasInputGroups));
        ReportCommand.NotifyCanExecuteChanged();
    }

    /// <summary>실적 수량이 바뀌면 투입 LOT을 먼저 들어온 것부터 다시 고른다.</summary>
    private void ReselectInputs()
    {
        foreach (var group in InputGroups)
        {
            group.SetUnits(TotalQty);
        }
    }

    partial void OnGoodQtyChanged(int value) => ReselectInputs();

    private void ResetInputs()
    {
        if (PendingReport != null)
        {
            return;   // 보내지 못한 실적이 있으면 그 수량을 화면에 그대로 둔다
        }
        GoodQty = 0;
        foreach (var d in Defects)
        {
            d.Qty = 0;
        }
    }

    private void OnDefectChanged()
    {
        OnPropertyChanged(nameof(DefectTotal));
        OnPropertyChanged(nameof(TotalQty));
        ReselectInputs();
        ReportCommand.NotifyCanExecuteChanged();
    }

    private void ShowInfo(string message)
    {
        IsError = false;
        Message = message;
    }

    private void ShowError(string message)
    {
        IsError = true;
        Message = message;
    }
}
