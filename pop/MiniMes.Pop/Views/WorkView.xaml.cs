using System.Globalization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;

namespace MiniMes.Pop.Views;

public partial class WorkView : UserControl
{
    public WorkView()
    {
        InitializeComponent();
    }
}

/// <summary>개수가 0이면 보이게 한다 (빈 목록 안내 문구용).</summary>
public sealed class ZeroToVisibleConverter : IValueConverter
{
    public static readonly ZeroToVisibleConverter Instance = new();

    public object Convert(object value, Type targetType, object parameter, CultureInfo culture) =>
        value is 0 ? Visibility.Visible : Visibility.Collapsed;

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture) =>
        throw new NotSupportedException();
}
