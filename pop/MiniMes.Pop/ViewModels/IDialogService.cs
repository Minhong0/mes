namespace MiniMes.Pop.ViewModels;

/// <summary>
/// 확인 창과 입력 창. 실제 창은 WpfDialogService가 띄운다.
/// </summary>
public interface IDialogService
{
    bool Confirm(string message);

    /// <summary>글자를 입력받는다. 취소하면 null</summary>
    string? AskText(string title, string prompt);
}
