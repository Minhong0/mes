using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace MiniMes.Pop.ViewModels;

/// <summary>불량 유형 한 줄 (예: 치수 초과 [-] 2 [+]). 장갑을 끼고도 누르기 쉽게 키보드 대신 버튼으로 센다.</summary>
public partial class DefectEntry(string code, string name, Action changed) : ObservableObject
{
    public string Code { get; } = code;

    public string Name { get; } = name;

    [ObservableProperty]
    [NotifyCanExecuteChangedFor(nameof(DecreaseCommand))]
    private int _qty;

    partial void OnQtyChanged(int value) => changed();   // 불량 합계와 [등록] 버튼 상태를 다시 계산하게 알린다

    [RelayCommand]
    private void Increase() => Qty++;

    private bool CanDecrease() => Qty > 0;

    [RelayCommand(CanExecute = nameof(CanDecrease))]
    private void Decrease() => Qty--;
}
