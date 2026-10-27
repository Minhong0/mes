using MiniMes.Pop.Api;
using MiniMes.Pop.ViewModels;

namespace MiniMes.Pop.Tests;

/// <summary>검사 탭: 실적이 만든 LOT을 골라 항목 × 시료 칸을 채우고 등록한다.</summary>
public class InspectionViewModelTests
{
    private readonly FakeMesApi _api = new();
    private string? _message;
    private bool _isError;

    private InspectionViewModel Open(params string[] lotNos)
    {
        var vm = new InspectionViewModel(_api,
            (message, isError) => (_message, _isError) = (message, isError));
        vm.ShowResults(lotNos.Select((lotNo, i) => Result(i + 1, lotNo)));
        return vm;
    }

    private static WorkResult Result(long id, string lotNo) =>
        new(id, 11, "CNC01", "W1001", DateTime.Today, DateTime.Now, 5, 0, false, null, [], [lotNo], []);

    /// <summary>외경 줄, 표면 거칠기 줄을 시료 순서대로 채운다</summary>
    private static void Fill(InspectionViewModel vm, string[] od, string[] ra)
    {
        for (var i = 0; i < od.Length; i++)
        {
            vm.Rows[0].Cells[i].Text = od[i];
        }
        for (var i = 0; i < ra.Length; i++)
        {
            vm.Rows[1].Cells[i].Text = ra[i];
        }
    }

    [Fact(DisplayName = "실적이 만든 LOT이 최근 것부터 나오고, 첫 LOT의 검사 항목이 시료 3개 칸으로 뜬다")]
    public void LotsAndEmptySheet()
    {
        var vm = Open("SF-SFT-010-261022-001", "SF-SFT-010-261022-002");

        Assert.Equal(["SF-SFT-010-261022-002", "SF-SFT-010-261022-001"], vm.Lots);
        Assert.Equal("SF-SFT-010-261022-002", vm.SelectedLot);
        Assert.Equal(["외경", "표면 거칠기"], vm.Rows.Select(r => r.Item.InspectName));
        Assert.Equal("37.95 ~ 38.05 mm", vm.Rows[0].RangeText);
        Assert.Equal("≤ 3.2 um", vm.Rows[1].RangeText);
        Assert.Equal([1, 2, 3], vm.SampleNumbers);
        Assert.All(vm.Rows, r => Assert.Equal(3, r.Cells.Count));
        Assert.False(vm.IsDone);
    }

    [Fact(DisplayName = "칸에 값을 넣으면 바로 OK/NG를 보여 주고, 숫자가 아니거나 빈 칸이 있으면 등록할 수 없다")]
    public void LiveJudgement()
    {
        var vm = Open("L1");
        Fill(vm, ["38.00", "38.07", "abc"], ["0.5", "3.2", ""]);

        Assert.Equal(["OK", "NG", "?"], vm.Rows[0].Cells.Select(c => c.Judgement));
        Assert.Equal("OK", vm.Rows[1].Cells[1].Judgement);   // 경계값은 합격
        Assert.Null(vm.Rows[1].Cells[2].Judgement);
        Assert.False(vm.SaveCommand.CanExecute(null));

        Fill(vm, ["38.00", "38.07", "38.01"], ["0.5", "3.2", "1"]);
        Assert.True(vm.SaveCommand.CanExecute(null));
    }

    [Fact(DisplayName = "시료 수를 바꾸면 칸이 늘고 줄며, 이미 적은 값은 남는다 (1~10개)")]
    public void ChangeSampleCount()
    {
        var vm = Open("L1");
        Fill(vm, ["38.00", "38.01"], ["1", "2"]);

        vm.RemoveSampleCommand.Execute(null);
        vm.AddSampleCommand.Execute(null);
        vm.AddSampleCommand.Execute(null);

        Assert.Equal(4, vm.SampleCount);
        Assert.Equal([1, 2, 3, 4], vm.SampleNumbers);
        Assert.Equal(["38.00", "38.01", "", ""], vm.Rows[0].Cells.Select(c => c.Text));
        for (var i = 0; i < 10; i++)
        {
            vm.AddSampleCommand.Execute(null);
        }
        Assert.Equal(InspectionViewModel.MaxSamples, vm.SampleCount);
    }

    [Fact(DisplayName = "합격: 항목 × 시료를 모두 보내고, 결과를 읽기 전용으로 보여 준다")]
    public async Task SavePass()
    {
        var vm = Open("L1");
        Fill(vm, ["38.00", "38.01", "37.99"], ["0.5", "0.6", "0.7"]);

        await vm.SaveCommand.ExecuteAsync(null);

        var sent = _api.Inspections.Single();
        Assert.Equal("L1", sent.LotNo);
        Assert.Equal(6, sent.Request.Measurements.Count);
        Assert.Contains(new Measurement("OD", 2, 38.01m), sent.Request.Measurements);
        Assert.False(_isError);
        Assert.Equal("L1 검사 합격 (시료 3개)", _message);
        Assert.True(vm.IsDone);
        Assert.StartsWith("합격", vm.ResultText);
        Assert.All(vm.Rows.SelectMany(r => r.Cells), c => Assert.True(c.IsReadOnly));
        Assert.False(vm.SaveCommand.CanExecute(null));
    }

    [Fact(DisplayName = "불합격: 보류됐다고 알리고, 다시 열어도 서버의 측정값과 판정을 보여 준다")]
    public async Task SaveFailHolds()
    {
        var vm = Open("L1");
        Fill(vm, ["38.00", "38.07", "38.01"], ["0.5", "0.6", "0.7"]);

        await vm.SaveCommand.ExecuteAsync(null);

        Assert.True(_isError);
        Assert.Contains("불합격", _message);
        Assert.Contains("보류한 LOT 1개: L1", _message);
        Assert.EndsWith("보류 중", vm.ResultText);

        await vm.LoadAsync();
        Assert.Equal(["38", "38.07", "38.01"], vm.Rows[0].Cells.Select(c => c.Text));
        Assert.Equal("NG", vm.Rows[0].Cells[1].Judgement);
    }

    [Fact(DisplayName = "응답이 없으면 입력을 그대로 두고 다시 누르게 한다")]
    public async Task RetryOnConnectionError()
    {
        _api.FailInspects = 1;
        var vm = Open("L1");
        Fill(vm, ["38.00", "38.01", "37.99"], ["0.5", "0.6", "0.7"]);

        await vm.SaveCommand.ExecuteAsync(null);
        Assert.True(_isError);
        Assert.Contains("다시 누르세요", _message);
        Assert.False(vm.IsDone);

        await vm.SaveCommand.ExecuteAsync(null);
        Assert.True(vm.IsDone);
        Assert.Equal(_api.Inspections[0].Request.Measurements, _api.Inspections[1].Request.Measurements);
    }

    [Fact(DisplayName = "검사 항목이 없는 공정의 LOT이면 안내만 보이고 등록할 수 없다")]
    public void NoSpecs()
    {
        _api.Sheets["FG-1"] = new InspectionSheet("FG-1", "FG-SFT-100", "ACTIVE", "OP-70", "조립 및 포장", null, 0, null, null, [], []);
        var vm = Open("FG-1");

        Assert.True(vm.NoSpecs);
        Assert.Empty(vm.Rows);
        Assert.False(vm.SaveCommand.CanExecute(null));
    }
}
