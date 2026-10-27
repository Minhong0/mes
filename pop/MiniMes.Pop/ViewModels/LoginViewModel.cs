using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using MiniMes.Pop.Api;

namespace MiniMes.Pop.ViewModels;

/// <summary>사번(사원증 바코드)과 비밀번호로 로그인한다.</summary>
public partial class LoginViewModel(IMesApi api, Action<Worker> onLoggedIn) : ObservableObject
{
    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(LoginCommand))]
    private string _workerId = "";

    /// <summary>PasswordBox는 바인딩이 안 돼서 LoginView가 글자를 칠 때마다 넣어 준다</summary>
    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(LoginCommand))]
    private string _password = "";

    [ObservableProperty]
    private string? _message;

    [ObservableProperty]
    private bool _isBusy;

    private bool CanLogin() => !string.IsNullOrWhiteSpace(WorkerId) && Password.Length > 0;

    [RelayCommand(CanExecute = nameof(CanLogin))]
    private async Task LoginAsync()
    {
        IsBusy = true;
        Message = null;
        try
        {
            onLoggedIn(await api.LoginAsync(WorkerId.Trim(), Password));
        }
        catch (MesApiException e)
        {
            Message = e.Message;   // 예: 사번 또는 비밀번호가 맞지 않습니다.
        }
        catch (MesConnectionException e)
        {
            Message = e.Message;
        }
        finally
        {
            IsBusy = false;
        }
    }
}
