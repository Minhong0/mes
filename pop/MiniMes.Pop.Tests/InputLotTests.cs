using MiniMes.Pop.Api;
using MiniMes.Pop.ViewModels;

namespace MiniMes.Pop.Tests;

/// <summary>투입 LOT 고르기: 환봉 LOT 두 개(3개, 10개 남음)가 있는 선삭 공정</summary>
public class InputLotTests
{
    private readonly FakeMesApi _api = new() { Inputs = [FakeMesApi.BarStock()] };

    private async Task<WorkViewModel> OpenAsync()
    {
        var vm = new WorkViewModel(_api, new AlwaysYesDialogs(), new MemoryPendingReportStore(),
            new Worker("W1001", "김도윤", "WC-100", "OPERATOR"), () => { });
        await vm.InitializeAsync();
        vm.SelectedOperation = vm.Operations.Single();
        await Task.Yield();
        return vm;
    }

    [Fact(DisplayName = "작업을 고르면 투입할 품목과 LOT이 뜨고, 수량(양품 + 불량)만큼 먼저 들어온 LOT부터 채운다")]
    public async Task FifoByTotal()
    {
        var vm = await OpenAsync();
        var group = Assert.Single(vm.InputGroups);
        Assert.Equal("입고한 원자재", group.SourceText);

        vm.GoodQty = 4;
        vm.Defects[0].IncreaseCommand.Execute(null);   // 합계 5

        Assert.Equal([3m, 2m], group.Lots.Select(l => l.UseQty));
        Assert.Equal([true, true], group.Lots.Select(l => l.IsSelected));
        Assert.True(group.IsSatisfied);
        Assert.Equal("필요 5 · 투입 5", group.StatusText);
    }

    [Fact(DisplayName = "등록하면 투입한 LOT과 수량을 함께 보내고, 만든 LOT 번호를 알려 준다")]
    public async Task ReportSendsInputs()
    {
        var vm = await OpenAsync();
        vm.GoodQty = 5;

        await vm.ReportCommand.ExecuteAsync(null);

        var sent = Assert.Single(_api.Sent);
        Assert.Equal([new LotInput("RM-SCM-001-261017-001", 3), new LotInput("RM-SCM-001-261017-002", 2)], sent.Inputs);
        Assert.Contains("LOT SF-SFT-010-261017-001", vm.Message);
        Assert.False(vm.IsError);
    }

    [Fact(DisplayName = "다른 LOT에 체크하면 체크한 LOT 안에서 다시 나눠 담는다")]
    public async Task ManualSelection()
    {
        var vm = await OpenAsync();
        vm.GoodQty = 2;
        var group = vm.InputGroups.Single();
        Assert.Equal([2m, 0m], group.Lots.Select(l => l.UseQty));

        group.Lots[1].IsSelected = true;
        group.Lots[0].IsSelected = false;

        Assert.Equal([0m, 2m], group.Lots.Select(l => l.UseQty));
        await vm.ReportCommand.ExecuteAsync(null);
        Assert.Equal([new LotInput("RM-SCM-001-261017-002", 2)], _api.Sent.Single().Inputs);
    }

    [Fact(DisplayName = "LOT이 모자라면 부족 수량을 보여 주고 등록할 수 없다")]
    public async Task ShortageBlocksReport()
    {
        var vm = await OpenAsync();
        vm.GoodQty = 15;
        var group = vm.InputGroups.Single();

        Assert.False(group.IsSatisfied);
        Assert.Equal("필요 15 · 투입 13 (부족 2)", group.StatusText);
        Assert.False(vm.ReportCommand.CanExecute(null));

        vm.GoodQty = 13;
        Assert.True(vm.ReportCommand.CanExecute(null));

        group.Lots[0].IsSelected = false;   // 직접 뺀 LOT은 다시 채우지 않는다
        Assert.False(vm.ReportCommand.CanExecute(null));
    }
}
