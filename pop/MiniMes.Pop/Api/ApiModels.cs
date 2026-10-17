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

/// <summary>실적 등록 요청. RequestId: 다시 보내도 한 번만 저장되도록 POP가 만든 GUID</summary>
public sealed record ReportRequest(string RequestId, string WorkerId, string? EquipCode, int GoodQty, IReadOnlyList<DefectLine> Defects);

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
    IReadOnlyList<DefectLine> Defects);

/// <summary>서버 오류 응답 (common.error.ErrorResponse)</summary>
public sealed record ApiError(string Code, string Message);
