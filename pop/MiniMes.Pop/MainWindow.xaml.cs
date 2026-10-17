using System.Windows;

namespace MiniMes.Pop;

/// <summary>화면 전환은 MainViewModel.CurrentPage가 맡으므로, 창 자체에는 코드가 없다.</summary>
public partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
    }
}
