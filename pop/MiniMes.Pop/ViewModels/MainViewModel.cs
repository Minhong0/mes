using CommunityToolkit.Mvvm.ComponentModel;
using MiniMes.Pop.Api;
using MiniMes.Pop.Storage;

namespace MiniMes.Pop.ViewModels;

/// <summary>
/// 창 하나에 화면 두 개(로그인, 작업)를 번갈아 보여 준다.
/// CurrentPage에 어떤 ViewModel을 넣느냐로 화면이 바뀐다. 어떤 화면을 그릴지는 MainWindow.xaml의 DataTemplate이 정한다.
/// 로그인이 만료되면(서버 401) 사번을 채운 로그인 화면으로 돌아간다. 보내지 못한 실적은 단말 파일에 남아 있어
/// 같은 사번으로 다시 로그인하면 작업 화면이 뜨면서 이어서 보낸다.
/// </summary>
public partial class MainViewModel : ObservableObject
{
    private readonly IMesApi _api;
    private readonly IDialogService _dialogs;
    private readonly IPendingReportStore _pendingStore;

    [ObservableProperty]
    private ObservableObject _currentPage;

    public MainViewModel(IMesApi api, IDialogService dialogs, IPendingReportStore pendingStore)
    {
        _api = api;
        _dialogs = dialogs;
        _pendingStore = pendingStore;
        _currentPage = new LoginViewModel(api, OnLoggedIn);
        _api.SessionExpired += (_, _) => OnSessionExpired();
    }

    public const string ExpiredMessage = "로그인이 만료되었습니다. 다시 로그인하세요.";

    private void OnSessionExpired()
    {
        var workerId = (CurrentPage as WorkViewModel)?.Worker.WorkerId ?? "";
        CurrentPage = new LoginViewModel(_api, OnLoggedIn) { WorkerId = workerId, Message = ExpiredMessage };
    }

    private async void OnLoggedIn(Worker worker)
    {
        var work = new WorkViewModel(_api, _dialogs, _pendingStore, worker, Logout);
        CurrentPage = work;
        await work.InitializeAsync();
    }

    private void Logout()
    {
        _api.Logout();
        CurrentPage = new LoginViewModel(_api, OnLoggedIn);
    }
}
