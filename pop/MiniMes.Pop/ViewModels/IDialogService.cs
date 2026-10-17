namespace MiniMes.Pop.ViewModels;

/// <summary>
/// 확인 창과 입력 창. ViewModel이 MessageBox 같은 WPF 창을 직접 띄우면 테스트에서 창이 떠서 멈춘다.
/// 그래서 "물어본다"는 일만 이 인터페이스로 빼 두고, 실제 창은 WpfDialogService가 띄운다.
/// </summary>
public interface IDialogService
{
    bool Confirm(string message);

    /// <summary>글자를 입력받는다. 취소하면 null</summary>
    string? AskText(string title, string prompt);
}
