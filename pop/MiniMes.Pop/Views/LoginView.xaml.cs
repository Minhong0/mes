using System.Windows.Controls;

namespace MiniMes.Pop.Views;

public partial class LoginView : UserControl
{
    public LoginView()
    {
        InitializeComponent();
        Loaded += (_, _) => WorkerIdBox.Focus();   // 화면이 뜨면 바로 바코드를 찍을 수 있게
    }
}
