using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using MiniMes.Pop.Api;

namespace MiniMes.Pop.ViewModels;

/// <summary>
/// 투입할 품목 하나(예: SCM440 환봉)와 쓸 수 있는 LOT들.
/// 실적 수량(양품 + 불량)이 바뀌면 먼저 들어온 LOT부터 필요한 만큼 골라 나눠 담는다 (선입선출).
/// 작업자가 체크를 바꾸면, 체크한 LOT들 안에서 먼저 들어온 것부터 다시 나눠 담는다.
/// </summary>
public partial class InputLotGroup : ObservableObject
{
    private readonly Action _changed;
    private int _units;
    private bool _selecting;

    public InputLotGroup(InputLots source, Action changed)
    {
        ItemCode = source.ItemCode;
        ItemName = source.ItemName;
        QtyPer = source.QtyPer;
        FromProcess = source.FromProcess;
        Lots = new ObservableCollection<InputLotEntry>(source.Lots.Select(l => new InputLotEntry(l, OnSelectionChanged)));
        _changed = changed;
    }

    public string ItemCode { get; }

    public string ItemName { get; }

    public decimal QtyPer { get; }

    public string? FromProcess { get; }

    public string SourceText => FromProcess == null ? "입고한 원자재" : $"앞 공정({FromProcess})에서 만든 LOT";

    public ObservableCollection<InputLotEntry> Lots { get; }

    public decimal Need => QtyPer * _units;

    public decimal Allocated => Lots.Sum(l => l.UseQty);

    public bool IsSatisfied => Allocated == Need;

    public string StatusText => IsSatisfied
        ? $"필요 {Need:0.###} · 투입 {Allocated:0.###}"
        : $"필요 {Need:0.###} · 투입 {Allocated:0.###} (부족 {Need - Allocated:0.###})";

    /// <summary>수량이 바뀌었다: 먼저 들어온 LOT부터 필요한 만큼 다시 고른다.</summary>
    public void SetUnits(int units)
    {
        _units = units;
        _selecting = true;   // 체크를 한꺼번에 바꾸는 동안에는 LOT마다 다시 나누지 않는다
        var left = Need;
        foreach (var lot in Lots)
        {
            lot.IsSelected = left > 0;
            left -= lot.IsSelected ? lot.Qty : 0;
        }
        _selecting = false;
        Distribute();
    }

    /// <summary>서버에 보낼 투입 목록: 실제로 담은 LOT만</summary>
    public IEnumerable<LotInput> ToInputs() =>
        Lots.Where(l => l.UseQty > 0).Select(l => new LotInput(l.LotNo, l.UseQty));

    private void OnSelectionChanged()
    {
        if (!_selecting)
        {
            Distribute();
        }
    }

    private void Distribute()
    {
        var left = Need;
        foreach (var lot in Lots)
        {
            var use = lot.IsSelected ? Math.Min(lot.Qty, left) : 0;
            lot.UseQty = use;
            left -= use;
        }
        OnPropertyChanged(nameof(Need));
        OnPropertyChanged(nameof(Allocated));
        OnPropertyChanged(nameof(IsSatisfied));
        OnPropertyChanged(nameof(StatusText));
        _changed();   // [실적 등록] 버튼 상태를 다시 계산하게 알린다
    }
}

/// <summary>투입 후보 LOT 한 줄: 체크하면 이 LOT에서 꺼내 쓴다. UseQty: 이번 실적에 쓸 수량</summary>
public partial class InputLotEntry(LotStock source, Action selectionChanged) : ObservableObject
{
    public string LotNo => source.LotNo;

    /// <summary>남은 수량</summary>
    public decimal Qty => source.Qty;

    public DateTime CreatedAt => source.CreatedAt;

    [ObservableProperty]
    private bool _isSelected;

    [ObservableProperty]
    private decimal _useQty;

    partial void OnIsSelectedChanged(bool value) => selectionChanged();
}
