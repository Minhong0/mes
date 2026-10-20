using System.Windows;
using System.Windows.Controls;

namespace MiniMes.Pop.Views;

/// <summary>사유 같은 짧은 글을 입력받는 창.</summary>
public partial class TextInputDialog : Window
{
    public TextInputDialog(string title, string prompt)
    {
        InitializeComponent();
        Title = title;
        PromptText.Text = prompt;
        Loaded += (_, _) => AnswerBox.Focus();
    }

    public string Answer => AnswerBox.Text;

    private void QuickReason_Click(object sender, RoutedEventArgs e) => AnswerBox.Text = (string)((Button)sender).Content;

    private void Ok_Click(object sender, RoutedEventArgs e)
    {
        if (string.IsNullOrWhiteSpace(AnswerBox.Text))
        {
            AnswerBox.Focus();
            return;
        }
        DialogResult = true;
    }
}
