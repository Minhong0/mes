namespace MiniMes.Pop.Api;

// 서버 API의 JSON과 같은 모양의 record들. 필드 이름은 서버(Java)의 camelCase JSON과 대소문자만 다르다
// (MesApiClient가 JsonSerializerDefaults.Web으로 맞춘다).

public sealed record Worker(string WorkerId, string WorkerName, string? WcCode, string Role);

/// <summary>로그인 응답. AccessToken은 요청마다 Authorization: Bearer로 보낸다 (MesApiClient가 붙인다)</summary>
public sealed record LoginResponse(string AccessToken, string TokenType, DateTimeOffset ExpiresAt, Worker Worker);

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
/// Inputs: 투입한 LOT. 품목마다 합이 (양품 + 불량) × 1개당 투입 수량이어야 한다.
/// 누가 했는지는 보내지 않는다: 서버가 로그인 토큰의 사번으로 남긴다
/// </summary>
public sealed record ReportRequest(string RequestId, string? EquipCode, int GoodQty, IReadOnlyList<DefectLine> Defects,
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
    IReadOnlyList<string>? OutputLotNos,
    IReadOnlyList<LotInput>? Inputs)
{
    /// <summary>만든 LOT을 한 줄로 (포장처럼 나뉘면 여러 개). 없으면 null</summary>
    public string? OutputLotText => OutputLotNos is { Count: > 0 } ? string.Join(", ", OutputLotNos) : null;
}

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
public sealed record ReasonRequest(string DtCat, string? DtSub, string? Note);

/// <summary>
/// LOT 검사표: 그 LOT을 만든 공정의 검사 항목과, 검사했으면 시료별 측정값.
/// Judgement: 검사 전이면 null, 하나라도 NG면 "NG". HeldLotNos: 이번 검사가 불합격이라 보류한 LOT (이 LOT과 하위 LOT)
/// </summary>
public sealed record InspectionSheet(
    string LotNo,
    string ItemCode,
    string Status,
    string? ProcessCode,
    string? ProcessName,
    string? Judgement,
    int SampleCount,
    string? WorkerId,
    DateTime? MeasuredAt,
    IReadOnlyList<InspectionItem> Items,
    IReadOnlyList<string> HeldLotNos);

/// <summary>검사 항목 하나. Lsl·Usl 중 하나는 없을 수 있다 (예: 표면 거칠기는 상한만)</summary>
public sealed record InspectionItem(
    string InspectCode,
    string InspectName,
    string Unit,
    decimal? Nominal,
    decimal? Lsl,
    decimal? Usl,
    string? EquipCode,
    IReadOnlyList<InspectionSample> Samples);

public sealed record InspectionSample(int SampleNo, decimal Value, string Judgement);

/// <summary>검사 등록 요청. 모든 항목을 시료 1번부터 n번까지</summary>
public sealed record InspectRequest(IReadOnlyList<Measurement> Measurements);

public sealed record Measurement(string InspectCode, int SampleNo, decimal Value);

/// <summary>서버 오류 응답 (common.error.ErrorResponse)</summary>
public sealed record ApiError(string Code, string Message);
