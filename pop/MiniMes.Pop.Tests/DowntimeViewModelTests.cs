using MiniMes.Pop.Api;
using MiniMes.Pop.ViewModels;

namespace MiniMes.Pop.Tests;

/// <summary>설비 정지 탭: 정지 목록과 사유 확정</summary>
public class DowntimeViewModelTests
{
    private readonly FakeMesApi _api = new();
    private readonly List<(string Message, bool IsError)> _messages = [];

    private async Task<DowntimeViewModel> OpenAsync()
    {
        var vm = new DowntimeViewModel(_api, (m, e) => _messages.Add((m, e)));
        await vm.InitializeAsync();
        await vm.ShowEquipmentAsync("CNC01");
        return vm;
    }

    [Fact(DisplayName = "최근 정지를 사유 이름과 함께 보여 주고, 아직 확정하지 않은 정지 수를 탭 머리에 띄운다")]
    public async Task List()
    {
        var vm = await OpenAsync();

        Assert.Equal(["설비고장 · 스핀들 이상", "자재대기"], vm.Downtimes.Select(d => d.ReasonText));
        Assert.Equal(["정지 중", "20분"], vm.Downtimes.Select(d => d.DurationText));
        Assert.Equal(1, vm.UnconfirmedCount);
        Assert.Equal("설비 정지 (미확인 1)", vm.TabHeader);
    }

    [Fact(DisplayName = "정지를 고르면 지금 사유가 채워지고, 소분류는 고른 대분류의 것만 보인다")]
    public async Task SelectFillsReason()
    {
        var vm = await OpenAsync();

        vm.SelectedDowntime = vm.Downtimes[0];

        Assert.Equal("BREAKDOWN", vm.SelectedCategory?.Code);
        Assert.Equal("BRK_SPINDLE", vm.SelectedSubReason?.Code);
        Assert.Equal(["BRK_SPINDLE", "BRK_SENSOR"], vm.SubReasons.Select(s => s.Code));

        vm.SelectedCategory = vm.Categories.Single(c => c.Code == "MATERIAL");

        Assert.Equal(["MAT_NOT_RECEIVED"], vm.SubReasons.Select(s => s.Code));
        Assert.Null(vm.SelectedSubReason);   // 다른 대분류의 소분류는 비운다
    }

    [Fact(DisplayName = "사유 저장: 대분류·소분류·메모를 보내고, 그 줄이 확정으로 바뀐다")]
    public async Task SaveReason()
    {
        var vm = await OpenAsync();
        vm.SelectedDowntime = vm.Downtimes[0];
        vm.SelectedCategory = vm.Categories.Single(c => c.Code == "MATERIAL");
        vm.SelectedSubReason = vm.SubReasons.Single();
        vm.Note = "  소재 입고 지연 ";

        await vm.SaveReasonCommand.ExecuteAsync(null);

        var (id, request) = Assert.Single(_api.ReasonsSent);
        Assert.Equal(2, id);
        Assert.Equal(new ReasonRequest("MATERIAL", "MAT_NOT_RECEIVED", "소재 입고 지연"), request);
        Assert.Equal("자재대기 · 원자재 미입고", vm.Downtimes[0].ReasonText);
        Assert.True(vm.Downtimes[0].IsConfirmed);
        Assert.Same(vm.Downtimes[0], vm.SelectedDowntime);
        Assert.Equal("설비 정지", vm.TabHeader);
        Assert.False(_messages.Last().IsError);
    }

    [Fact(DisplayName = "정지를 고르지 않았거나 대분류가 없으면 저장할 수 없다")]
    public async Task CannotSaveWithoutCategory()
    {
        var vm = await OpenAsync();
        Assert.False(vm.SaveReasonCommand.CanExecute(null));

        vm.SelectedDowntime = vm.Downtimes[0];
        vm.SelectedCategory = null;

        Assert.False(vm.SaveReasonCommand.CanExecute(null));
    }

    [Fact(DisplayName = "응답이 없으면 그대로 두고 다시 누르라고 알린다 (같은 내용이라 다시 보내도 된다)")]
    public async Task ConnectionFailure()
    {
        _api.FailReasons = 1;
        var vm = await OpenAsync();
        vm.SelectedDowntime = vm.Downtimes[0];

        await vm.SaveReasonCommand.ExecuteAsync(null);

        Assert.False(vm.Downtimes[0].IsConfirmed);
        Assert.True(_messages.Last().IsError);
        Assert.Contains("다시 누르세요", _messages.Last().Message);

        await vm.SaveReasonCommand.ExecuteAsync(null);

        Assert.True(vm.Downtimes[0].IsConfirmed);
    }

    [Fact(DisplayName = "설비를 바꾸면 화면이 설비 코드를 새로 받는다 (변경 알림)")]
    public async Task EquipCodeNotifies()
    {
        var vm = await OpenAsync();
        var changed = new List<string?>();
        vm.PropertyChanged += (_, e) => changed.Add(e.PropertyName);

        await vm.ShowEquipmentAsync("CNC03");

        Assert.Contains(nameof(DowntimeViewModel.EquipCode), changed);
        Assert.Empty(vm.Downtimes);
    }

    [Fact(DisplayName = "목록을 새로 읽어도 고른 정지는 그대로 골라 둔다")]
    public async Task ReloadKeepsSelection()
    {
        var vm = await OpenAsync();
        vm.SelectedDowntime = vm.Downtimes[1];

        await vm.LoadCommand.ExecuteAsync(null);

        Assert.Equal(1, vm.SelectedDowntime?.Source.DowntimeId);
    }
}
