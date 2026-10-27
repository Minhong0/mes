using MiniMes.Pop.ViewModels;

namespace MiniMes.Pop.Tests;

/// <summary>로그인 → 작업 화면, 로그인이 만료되면 다시 로그인 화면.</summary>
public class MainViewModelTests
{
    private readonly FakeMesApi _api = new();
    private readonly MemoryPendingReportStore _store = new();

    private async Task<MainViewModel> LoggedInAsync()
    {
        var main = new MainViewModel(_api, new AlwaysYesDialogs(), _store);
        var login = Assert.IsType<LoginViewModel>(main.CurrentPage);
        login.WorkerId = "W1001";
        login.Password = "1234";
        await login.LoginCommand.ExecuteAsync(null);
        Assert.IsType<WorkViewModel>(main.CurrentPage);
        return main;
    }

    [Fact(DisplayName = "로그인이 만료되면(서버 401) 사번을 채운 로그인 화면으로 돌아가고 만료 안내를 보여 준다")]
    public async Task SessionExpired()
    {
        var main = await LoggedInAsync();

        _api.ExpireSession();

        var login = Assert.IsType<LoginViewModel>(main.CurrentPage);
        Assert.Equal("W1001", login.WorkerId);
        Assert.Equal(MainViewModel.ExpiredMessage, login.Message);
    }

    [Fact(DisplayName = "만료 중에 보낸 실적은 단말에 남아, 같은 사번으로 다시 로그인하면 이어서 보낸다")]
    public async Task PendingReportSurvivesExpiry()
    {
        var main = await LoggedInAsync();
        var work = (WorkViewModel)main.CurrentPage;
        work.SelectedOperation = work.Operations.Single();
        work.AddGoodCommand.Execute("5");
        _api.Logout();   // 토큰이 만료된 상태에서 보낸다

        await work.ReportCommand.ExecuteAsync(null);

        Assert.Empty(_api.Saved);
        Assert.NotNull(_store.Load("W1001"));   // 저장되지 않았으니 단말 파일을 지우지 않는다

        _api.ExpireSession();
        var login = (LoginViewModel)main.CurrentPage;
        login.Password = "1234";
        await login.LoginCommand.ExecuteAsync(null);
        Assert.IsType<WorkViewModel>(main.CurrentPage);

        // 작업 화면이 뜨면서 남은 실적을 같은 요청 ID로 다시 보낸다
        Assert.Equal(5, Assert.Single(_api.Saved).GoodQty);
        Assert.Null(_store.Load("W1001"));
    }
}
