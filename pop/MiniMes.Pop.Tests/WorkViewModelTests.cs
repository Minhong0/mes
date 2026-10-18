using MiniMes.Pop.Api;
using MiniMes.Pop.ViewModels;

namespace MiniMes.Pop.Tests;

/// <summary>작업 화면의 로직. 창을 띄우지 않고 ViewModel의 명령을 직접 실행해 본다.</summary>
public class WorkViewModelTests
{
    private readonly FakeMesApi _api = new();
    private readonly MemoryPendingReportStore _store = new();

    private async Task<WorkViewModel> OpenAsync()
    {
        var vm = new WorkViewModel(_api, new AlwaysYesDialogs(), _store, new Worker("W1001", "김도윤", "WC-100", "OPERATOR"), () => { });
        await vm.InitializeAsync();
        vm.SelectedOperation = vm.Operations.Single();
        return vm;
    }

    [Fact(DisplayName = "소속 작업장(WC-100)과 그 작업장 설비(CNC01)가 기본으로 골라지고, 작업 목록과 불량 유형이 뜬다")]
    public async Task Initialize()
    {
        var vm = await OpenAsync();

        Assert.Equal("WC-100", vm.SelectedWorkCenter);
        Assert.Equal("CNC01", vm.SelectedEquipment?.EquipCode);
        Assert.Equal(["WC-100", "WC-200"], vm.WorkCenters);
        Assert.Equal(["OVERSIZE", "SCRATCH"], vm.Defects.Select(d => d.Code));
        Assert.Single(vm.Operations);
    }

    [Fact(DisplayName = "실적 등록: 양품·불량(0개인 유형은 빼고)과 설비를 보내고, 입력을 비우고, 목록을 새로 받는다")]
    public async Task Report()
    {
        var vm = await OpenAsync();
        vm.AddGoodCommand.Execute("10");
        vm.AddGoodCommand.Execute("-1");
        vm.Defects[0].IncreaseCommand.Execute(null);

        await vm.ReportCommand.ExecuteAsync(null);

        var sent = Assert.Single(_api.Sent);
        Assert.Equal(9, sent.GoodQty);
        Assert.Equal("CNC01", sent.EquipCode);
        Assert.Equal([new DefectLine("OVERSIZE", 1)], sent.Defects);
        Assert.Equal(0, vm.GoodQty);
        Assert.Equal(0, vm.DefectTotal);
        Assert.Equal(9, vm.SelectedOperation?.GoodQty);   // 새로 받은 목록의 값
        Assert.Single(vm.Results);
        Assert.False(vm.IsError);
    }

    [Fact(DisplayName = "수량이 0이면 등록할 수 없고, 진행 중이 아닌 공정은 등록할 수 없다")]
    public async Task CannotReportNothing()
    {
        _api.Operation = FakeMesApi.NewOperation("WAIT", 0, 0);
        var vm = await OpenAsync();
        vm.GoodQty = 5;
        Assert.False(vm.ReportCommand.CanExecute(null));   // 대기 상태
        Assert.True(vm.StartCommand.CanExecute(null));

        await vm.StartCommand.ExecuteAsync(null);
        vm.GoodQty = 0;

        Assert.Equal("RUN", vm.SelectedOperation?.Status);
        Assert.False(vm.ReportCommand.CanExecute(null));   // 수량 0
        vm.GoodQty = 1;
        Assert.True(vm.ReportCommand.CanExecute(null));
    }

    [Fact(DisplayName = "응답이 없으면 요청을 잡아 두고 입력을 잠근다. 다시 보내면 같은 requestId라 한 번만 저장된다")]
    public async Task RetrySameRequest()
    {
        _api.FailReports = 1;
        _api.SaveBeforeFail = true;   // 서버는 저장했지만 응답을 잃은 가장 까다로운 경우
        var vm = await OpenAsync();
        vm.GoodQty = 7;

        await vm.ReportCommand.ExecuteAsync(null);

        Assert.True(vm.HasPendingReport);
        Assert.False(vm.IsInputEnabled);
        Assert.Equal("다시 보내기", vm.ReportButtonText);
        Assert.Equal(7, vm.GoodQty);                        // 입력한 수량은 그대로 보인다
        Assert.False(vm.StartCommand.CanExecute(null));
        Assert.True(vm.IsError);

        await vm.ReportCommand.ExecuteAsync(null);         // 다시 보내기

        Assert.Equal(2, _api.Sent.Count);
        Assert.Equal(_api.Sent[0].RequestId, _api.Sent[1].RequestId);
        Assert.Single(_api.Saved);                          // 두 번 보냈지만 한 번 저장
        Assert.False(vm.HasPendingReport);
        Assert.True(vm.IsInputEnabled);
        Assert.Equal(7, vm.SelectedOperation?.GoodQty);
    }

    [Fact(DisplayName = "서버가 거절하면(입력 오류) 잡아 두지 않는다: 저장되지 않은 것이 확실하다")]
    public async Task RejectedIsNotPending()
    {
        var vm = await OpenAsync();
        vm.GoodQty = 1000;   // 가짜 서버는 계획 수량(100)을 넘으면 거절한다

        await vm.ReportCommand.ExecuteAsync(null);

        Assert.False(vm.HasPendingReport);
        Assert.True(vm.IsError);
        Assert.Equal("계획 수량을 넘었습니다.", vm.Message);
        Assert.Empty(_api.Saved);
    }

    [Fact(DisplayName = "보내지 못한 실적을 버리면 입력이 풀리고 실적 목록을 다시 읽는다 (실제로는 저장됐을 수 있으므로)")]
    public async Task DiscardPending()
    {
        _api.FailReports = 1;
        _api.SaveBeforeFail = true;
        var vm = await OpenAsync();
        vm.GoodQty = 3;
        await vm.ReportCommand.ExecuteAsync(null);

        await vm.DiscardPendingCommand.ExecuteAsync(null);

        Assert.False(vm.HasPendingReport);
        Assert.Equal(0, vm.GoodQty);
        Assert.Single(vm.Results);   // 서버에 저장돼 있던 실적이 목록에 보인다
    }

    [Fact(DisplayName = "목록을 새로 받아도 보던 작업과 입력 중인 수량은 그대로 둔다")]
    public async Task RefreshKeepsInput()
    {
        var vm = await OpenAsync();
        vm.GoodQty = 4;

        await vm.RefreshCommand.ExecuteAsync(null);

        Assert.Equal(11, vm.SelectedOperation?.WoOpId);
        Assert.Equal(4, vm.GoodQty);
    }

    [Fact(DisplayName = "공정을 완료하면 목록에서 빠지고 선택이 풀린다")]
    public async Task Complete()
    {
        var vm = await OpenAsync();

        await vm.CompleteCommand.ExecuteAsync(null);

        Assert.Equal("DONE", _api.Operation.Status);
        Assert.Null(vm.SelectedOperation);
    }

    [Fact(DisplayName = "실적은 보내기 전에 단말에 적고, 저장되면 지운다")]
    public async Task SavedBeforeSending()
    {
        var vm = await OpenAsync();
        vm.GoodQty = 5;

        await vm.ReportCommand.ExecuteAsync(null);

        var saved = Assert.Single(_store.Saves);
        Assert.Equal(_api.Sent[0].RequestId, saved.Request.RequestId);
        Assert.Equal(11, saved.WoOpId);
        Assert.Equal("WC-100", saved.WcCode);
        Assert.Null(_store.Load("W1001"));
    }

    [Fact(DisplayName = "응답을 기다리다 POP가 꺼져도, 다음 로그인 때 같은 requestId로 다시 보내 한 번만 저장된다")]
    public async Task ResendAfterRestart()
    {
        _api.FailReports = 1;
        _api.SaveBeforeFail = true;
        var first = await OpenAsync();
        first.GoodQty = 6;
        await first.ReportCommand.ExecuteAsync(null);
        Assert.NotNull(_store.Load("W1001"));   // 여기서 POP가 꺼졌다고 친다

        var second = await OpenAsync();          // 다시 로그인: 같은 단말 저장소

        Assert.Equal(2, _api.Sent.Count);
        Assert.Equal(_api.Sent[0].RequestId, _api.Sent[1].RequestId);
        Assert.Single(_api.Saved);
        Assert.Null(_store.Load("W1001"));
        Assert.False(second.HasPendingReport);
        Assert.False(second.IsError);
        Assert.Contains("지난번에 결과를 모른 채 끝난 실적(WO-1 CNC 선삭", second.Message);
    }

    [Fact(DisplayName = "다시 로그인했는데 서버가 아직 응답이 없으면, 보내지 못한 실적으로 화면에 잡아 둔다")]
    public async Task RestoreStillOffline()
    {
        _api.FailReports = 2;
        var first = await OpenAsync();
        first.GoodQty = 6;
        first.Defects[1].IncreaseCommand.Execute(null);
        await first.ReportCommand.ExecuteAsync(null);

        var second = await OpenAsync();

        Assert.True(second.HasPendingReport);
        Assert.Equal(_api.Sent[0].RequestId, second.PendingReport?.RequestId);
        Assert.Equal(6, second.GoodQty);                 // 무엇을 보내려 했는지 보인다
        Assert.Equal(1, second.Defects[1].Qty);
        Assert.Equal(11, second.SelectedOperation?.WoOpId);
        Assert.Equal("다시 보내기", second.ReportButtonText);

        await second.ReportCommand.ExecuteAsync(null);    // 서버가 돌아왔다

        Assert.Single(_api.Saved);
        Assert.Null(_store.Load("W1001"));
    }

    [Fact(DisplayName = "지난번 실적을 서버가 거절하면 단말에서 지우고 이유를 보여 준다")]
    public async Task RestoreRejected()
    {
        _api.FailReports = 1;
        var first = await OpenAsync();
        first.GoodQty = 1000;   // 계획 수량(100)을 넘는다: 서버에 닿으면 거절된다
        await first.ReportCommand.ExecuteAsync(null);

        var second = await OpenAsync();

        Assert.False(second.HasPendingReport);
        Assert.Null(_store.Load("W1001"));
        Assert.True(second.IsError);
        Assert.Contains("계획 수량을 넘었습니다.", second.Message);
    }

    [Fact(DisplayName = "보내지 못한 실적을 버리면 단말에서도 지운다")]
    public async Task DiscardClearsStore()
    {
        _api.FailReports = 1;
        var vm = await OpenAsync();
        vm.GoodQty = 3;
        await vm.ReportCommand.ExecuteAsync(null);

        await vm.DiscardPendingCommand.ExecuteAsync(null);

        Assert.Null(_store.Load("W1001"));
    }

    [Fact(DisplayName = "작업 화면이 뜨면 고른 설비(CNC01)의 정지 목록도 뜬다")]
    public async Task DowntimesOfSelectedEquipment()
    {
        var vm = await OpenAsync();

        Assert.Equal("CNC01", vm.Downtime.EquipCode);
        Assert.Equal(2, vm.Downtime.Downtimes.Count);
        Assert.Equal("설비 정지 (미확인 1)", vm.Downtime.TabHeader);
    }
}
