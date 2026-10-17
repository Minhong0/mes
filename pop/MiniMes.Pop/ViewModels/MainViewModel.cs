using CommunityToolkit.Mvvm.ComponentModel;
using MiniMes.Pop.Api;

namespace MiniMes.Pop.ViewModels;

/// <summary>
/// 창 하나에 화면 두 개(로그인, 작업)를 번갈아 보여 준다.
/// CurrentPage에 어떤 ViewModel을 넣느냐로 화면이 바뀐다. 어떤 화면을 그릴지는 MainWindow.xaml의 DataTemplate이 정한다.
/// </summary>
public partial class MainViewModel : ObservableObject
{
    private readonly IMesApi _api;
    private readonly IDialogService _dialogs;

    [ObservableProperty]
    private ObservableObject _currentPage;

    public MainViewModel(IMesApi api, IDialogService dialogs)
    {
        _api = api;
        _dialogs = dialogs;
        _currentPage = new LoginViewModel(api, OnLoggedIn);
    }

    private async void OnLoggedIn(Worker worker)
    {
        var work = new WorkViewModel(_api, _dialogs, worker, Logout);
        CurrentPage = work;
        await work.InitializeAsync();
    }

    private void Logout() => CurrentPage = new LoginViewModel(_api, OnLoggedIn);
}
