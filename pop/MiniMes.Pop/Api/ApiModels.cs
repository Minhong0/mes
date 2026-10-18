namespace MiniMes.Pop.Api;

// 서버 API의 JSON과 같은 모양의 record들. 필드 이름은 서버(Java)의 camelCase JSON과 대소문자만 다르다
// (MesApiClient가 JsonSerializerDefaults.Web으로 맞춘다).

public sealed record Worker(string WorkerId, string WorkerName, string? WcCode, string Role);

public sealed record Equipment(string EquipCode, string EquipName, string? WcCode, bool AutoCollect);

public sealed record CommonCode(string Code, string CodeName, string? ParentCode);

/// <summary>작업 목록의 한 줄. PrevGoodQty: 앞 공정 양품 수량 (첫 공정이면 null)</summary>
public sealed record OperationItem(
    long WoOpId,
    string WoNo,
    int Seq,
    string ProcessCode,
    string ProcessName,
    string WcCode,
    string Status,
    int GoodQty,
    int DefectQty,
    DateTime? StartedAt,
    string ItemCode,
    string ItemName,
    int PlanQty,
    DateOnly? DueDate,
    string WoStatus,
    int? PrevGoodQty);

/// <summary>공정 시작·완료 응답</summary>
public sealed record OperationState(long WoOpId, int Seq, string ProcessCode, string WcCode, string Status, int GoodQty, int DefectQty);

public sealed record DefectLine(string DefectType, int Qty);

/// <summary>실적에 투입한 LOT 한 줄</summary>
public sealed record LotInput(string LotNo, decimal Qty);

/// <summary>
/// 실적 등록 요청. RequestId: 다시 보내도 한 번만 저장되도록 POP가 만든 GUID.
/// Inputs: 투입한 LOT. 품목마다 합이 (양품 + 불량) × 1개당 투입 수량이어야 한다
/// </summary>
public sealed record ReportRequest(string RequestId, string WorkerId, string? EquipCode, int GoodQty, IReadOnlyList<DefectLine> Defects,
    IReadOnlyList<LotInput> Inputs);

public sealed record WorkResult(
    long ResultId,
    long WoOpId,
    string? EquipCode,
    string? WorkerId,
    DateTime StartedAt,
    DateTime? EndedAt,
    int GoodQty,
    int DefectQty,
    bool Cancelled,
    string? CancelReason,
    IReadOnlyList<DefectLine> Defects,
    string? OutputLotNo,
    IReadOnlyList<LotInput>? Inputs);

/// <summary>
/// 공정에 투입할 품목 하나와 쓸 수 있는 LOT (먼저 들어온 것부터).
/// QtyPer: 산출 1개(양품 + 불량)당 투입 수량. FromProcess: 앞 공정 코드, null이면 입고한 원자재
/// </summary>
public sealed record InputLots(string ItemCode, string ItemName, decimal QtyPer, string? FromProcess, IReadOnlyList<LotStock> Lots);

public sealed record LotStock(string LotNo, decimal Qty, DateTime CreatedAt);

/// <summary>설비가 남긴 정지 한 건. EndedAt이 null이면 아직 정지 중, ReasonAt이 null이면 작업자가 사유를 확정하지 않았다</summary>
public sealed record Downtime(
    long DowntimeId,
    string EquipCode,
    DateTime StartedAt,
    DateTime? EndedAt,
    int? DurationMin,
    string DtCat,
    string? DtSub,
    string? Note,
    string? WorkerId,
    DateTime? ReasonAt);

/// <summary>정지 사유 확정 요청. DtSub, Note는 비워도 된다</summary>
public sealed record ReasonRequest(string WorkerId, string DtCat, string? DtSub, string? Note);

/// <summary>서버 오류 응답 (common.error.ErrorResponse)</summary>
public sealed record ApiError(string Code, string Message);
