using MiniMes.Pop.Api;
using MiniMes.Pop.ViewModels;

namespace MiniMes.Pop.Tests;

/// <summary>작업 화면의 로직. 창을 띄우지 않고 ViewModel의 명령을 직접 실행해 본다.</summary>
public class WorkViewModelTests
{
    private readonly FakeMesApi _api = new();

    private async Task<WorkViewModel> OpenAsync()
    {
        var vm = new WorkViewModel(_api, new AlwaysYesDialogs(), new Worker("W1001", "김도윤", "WC-100", "OPERATOR"), () => { });
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
}
