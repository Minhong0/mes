using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using MiniMes.Pop.Api;

namespace MiniMes.Pop.ViewModels;

/// <summary>사번으로 작업자를 확인한다. 현장에서는 보통 사원증 바코드를 찍으면 사번이 입력된다.</summary>
public partial class LoginViewModel(IMesApi api, Action<Worker> onLoggedIn) : ObservableObject
{
    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(LoginCommand))]
    private string _workerId = "";

    [ObservableProperty]
    private string? _message;

    [ObservableProperty]
    private bool _isBusy;

    private bool CanLogin() => !string.IsNullOrWhiteSpace(WorkerId);

    [RelayCommand(CanExecute = nameof(CanLogin))]
    private async Task LoginAsync()
    {
        IsBusy = true;
        Message = null;
        try
        {
            onLoggedIn(await api.GetWorkerAsync(WorkerId.Trim()));
        }
        catch (MesApiException e)
        {
            Message = e.Message;   // 예: 작업자를 찾을 수 없습니다: W0000
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
