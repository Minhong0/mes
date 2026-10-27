using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using MiniMes.Pop.ViewModels;

namespace MiniMes.Pop.Views;

public partial class LoginView : UserControl
{
    public LoginView()
    {
        InitializeComponent();
        // 화면이 뜨면 바로 바코드를 찍을 수 있게. 만료로 돌아와 사번이 이미 있으면 비밀번호부터
        Loaded += (_, _) =>
        {
            if (string.IsNullOrEmpty(WorkerIdBox.Text)) WorkerIdBox.Focus();
            else PasswordBox.Focus();
        };
    }

    private void WorkerIdBox_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter)
        {
            PasswordBox.Focus();
        }
    }

    // PasswordBox.Password는 보안상 바인딩할 수 없어서 바뀔 때마다 ViewModel에 넣는다
    private void PasswordBox_PasswordChanged(object sender, RoutedEventArgs e)
    {
        if (DataContext is LoginViewModel vm)
        {
            vm.Password = PasswordBox.Password;
        }
    }
}
