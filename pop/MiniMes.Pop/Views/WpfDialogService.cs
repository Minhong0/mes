using System.Windows;
using MiniMes.Pop.ViewModels;

namespace MiniMes.Pop.Views;

/// <summary>IDialogService의 실제 구현: WPF 창을 띄운다.</summary>
public sealed class WpfDialogService : IDialogService
{
    public bool Confirm(string message) =>
        MessageBox.Show(Application.Current.MainWindow, message, "확인", MessageBoxButton.OKCancel, MessageBoxImage.Question)
        == MessageBoxResult.OK;

    public string? AskText(string title, string prompt)
    {
        var dialog = new TextInputDialog(title, prompt) { Owner = Application.Current.MainWindow };
        return dialog.ShowDialog() == true ? dialog.Answer : null;
    }
}
