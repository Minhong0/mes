namespace MiniMes.Pop.Api;

/// <summary>
/// POP가 쓰는 서버 API. 화면 로직(ViewModel)은 이 인터페이스만 알고, 실제 HTTP 호출은 MesApiClient가 한다.
///
/// 실패는 두 가지로 나눠 던진다.
/// - MesApiException: 서버가 받아서 거절했다 (입력 오류, 상태 오류). 다시 보내도 결과가 같다.
/// - MesConnectionException: 서버에 닿지 못했거나 응답을 받지 못했다. 저장됐는지 알 수 없다.
/// </summary>
public interface IMesApi
{
    Task<Worker> GetWorkerAsync(string workerId);

    Task<IReadOnlyList<Equipment>> GetEquipmentAsync();

    Task<IReadOnlyList<CommonCode>> GetCodesAsync(string groupCode);

    Task<IReadOnlyList<OperationItem>> GetOpenOperationsAsync(string wcCode);

    Task<OperationState> StartAsync(long woOpId, string workerId);

    /// <summary>공정에 투입할 품목마다 쓸 수 있는 LOT</summary>
    Task<IReadOnlyList<InputLots>> GetInputLotsAsync(long woOpId);

    Task<WorkResult> ReportAsync(long woOpId, ReportRequest request);

    Task<OperationState> CompleteAsync(long woOpId, string workerId);

    Task<IReadOnlyList<WorkResult>> GetResultsAsync(long woOpId);

    Task<WorkResult> CancelResultAsync(long resultId, string workerId, string reason);

    /// <summary>설비의 최근 정지 (최근 것부터)</summary>
    Task<IReadOnlyList<Downtime>> GetDowntimesAsync(string equipCode, int limit);

    /// <summary>정지 사유 확정. 같은 내용으로 다시 보내도 결과가 같다</summary>
    Task<Downtime> ConfirmDowntimeReasonAsync(long downtimeId, ReasonRequest request);
}

/// <summary>서버가 요청을 거절했다. Code는 서버의 ErrorCode (예: INVALID_STATUS_TRANSITION)</summary>
public sealed class MesApiException(int statusCode, string code, string message) : Exception(message)
{
    public int StatusCode { get; } = statusCode;

    public string Code { get; } = code;
}

/// <summary>서버에 연결하지 못했거나 시간 안에 응답이 없었다.</summary>
public sealed class MesConnectionException(string message, Exception inner) : Exception(message, inner);
