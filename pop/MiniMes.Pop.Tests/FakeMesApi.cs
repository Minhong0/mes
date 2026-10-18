using MiniMes.Pop.Api;
using MiniMes.Pop.Storage;
using MiniMes.Pop.ViewModels;

namespace MiniMes.Pop.Tests;

/// <summary>
/// 서버 없이 화면 로직을 시험하는 가짜 API. 작업장 WC-100에 공정 하나(WO-1 선삭)가 있다.
/// 실제 서버처럼 requestId가 같은 실적은 한 번만 저장한다. CNC01에는 정지 두 건(하나는 정지 중)이 있다.
/// </summary>
public sealed class FakeMesApi : IMesApi
{
    public List<ReportRequest> Sent { get; } = [];

    public List<WorkResult> Saved { get; } = [];

    /// <summary>다음 실적 전송 몇 번을 "서버 응답 없음"으로 실패시킨다. saveBeforeFail이면 저장은 하고 응답만 잃는다.</summary>
    public int FailReports { get; set; }

    public bool SaveBeforeFail { get; set; }

    public OperationItem Operation { get; set; } = NewOperation("RUN", 0, 0);

    public static OperationItem NewOperation(string status, int good, int defect) =>
        new(11, "WO-1", 10, "OP-10", "CNC 선삭", "WC-100", status, good, defect, null,
            "FG-SFT-100", "샤프트", 100, null, "RUN", null);

    public Task<Worker> GetWorkerAsync(string workerId) => workerId == "W1001"
        ? Task.FromResult(new Worker("W1001", "김도윤", "WC-100", "OPERATOR"))
        : Task.FromException<Worker>(new MesApiException(404, "WORKER_NOT_FOUND", $"작업자를 찾을 수 없습니다: {workerId}"));

    public Task<IReadOnlyList<Equipment>> GetEquipmentAsync() => Task.FromResult<IReadOnlyList<Equipment>>(
    [
        new Equipment("CNC01", "CNC 선반 #1", "WC-100", true),
        new Equipment("CNC03", "CNC 밀링 #1", "WC-200", true),
    ]);

    public Task<IReadOnlyList<CommonCode>> GetCodesAsync(string groupCode) => Task.FromResult<IReadOnlyList<CommonCode>>(groupCode switch
    {
        "DEFECT_TYPE" => [new CommonCode("OVERSIZE", "치수 초과", null), new CommonCode("SCRATCH", "표면 흠집", null)],
        "DT_CAT" => [new CommonCode("MATERIAL", "자재대기", null), new CommonCode("BREAKDOWN", "설비고장", null)],
        "DT_SUB" =>
        [
            new CommonCode("MAT_NOT_RECEIVED", "원자재 미입고", "MATERIAL"),
            new CommonCode("BRK_SPINDLE", "스핀들 이상", "BREAKDOWN"),
            new CommonCode("BRK_SENSOR", "센서 불량", "BREAKDOWN"),
        ],
        _ => [],
    });

    public Task<IReadOnlyList<OperationItem>> GetOpenOperationsAsync(string wcCode) =>
        Task.FromResult<IReadOnlyList<OperationItem>>(
            wcCode == "WC-100" && Operation.Status is "WAIT" or "RUN" ? [Operation] : []);   // 서버처럼 끝난 공정은 빼고

    public Task<OperationState> StartAsync(long woOpId, string workerId)
    {
        Operation = Operation with { Status = "RUN" };
        return Task.FromResult(new OperationState(woOpId, 10, "OP-10", "WC-100", "RUN", 0, 0));
    }

    public Task<WorkResult> ReportAsync(long woOpId, ReportRequest request)
    {
        Sent.Add(request);
        if (FailReports > 0)
        {
            FailReports--;
            if (SaveBeforeFail)
            {
                SaveOnce(request);   // 서버는 저장했는데 응답이 돌아오지 못한 경우
            }
            return Task.FromException<WorkResult>(new MesConnectionException("서버 응답이 없습니다.", new TimeoutException()));
        }
        if (request.GoodQty > Operation.PlanQty)
        {
            return Task.FromException<WorkResult>(new MesApiException(400, "INVALID_INPUT", "계획 수량을 넘었습니다."));
        }
        return Task.FromResult(SaveOnce(request));
    }

    private readonly Dictionary<string, WorkResult> _byRequest = [];

    /// <summary>서버처럼 requestId가 같으면 저장하지 않고 처음 결과를 돌려준다.</summary>
    private WorkResult SaveOnce(ReportRequest request)
    {
        if (_byRequest.TryGetValue(request.RequestId, out var existing))
        {
            return existing;
        }
        var defects = request.Defects.Sum(d => d.Qty);
        var result = new WorkResult(Saved.Count + 1, 11, request.EquipCode, request.WorkerId, DateTime.Today, DateTime.Now,
            request.GoodQty, defects, false, null, request.Defects);
        Saved.Add(result);
        _byRequest[request.RequestId] = result;
        Operation = Operation with { GoodQty = Operation.GoodQty + request.GoodQty, DefectQty = Operation.DefectQty + defects };
        return result;
    }

    public Task<OperationState> CompleteAsync(long woOpId, string workerId)
    {
        Operation = Operation with { Status = "DONE" };
        return Task.FromResult(new OperationState(woOpId, 10, "OP-10", "WC-100", "DONE", Operation.GoodQty, Operation.DefectQty));
    }

    public Task<IReadOnlyList<WorkResult>> GetResultsAsync(long woOpId) => Task.FromResult<IReadOnlyList<WorkResult>>(Saved.ToList());

    public Task<WorkResult> CancelResultAsync(long resultId, string workerId, string reason) =>
        throw new NotSupportedException();

    public List<Downtime> Downtimes { get; } =
    [
        new(2, "CNC01", new DateTime(2026, 10, 17, 10, 0, 0), null, null, "BREAKDOWN", "BRK_SPINDLE", null, null, null),
        new(1, "CNC01", new DateTime(2026, 10, 17, 9, 0, 0), new DateTime(2026, 10, 17, 9, 20, 0), 20, "MATERIAL", null,
            "소재 늦음", "W1001", new DateTime(2026, 10, 17, 9, 30, 0)),
    ];

    public List<(long Id, ReasonRequest Request)> ReasonsSent { get; } = [];

    /// <summary>다음 사유 저장 몇 번을 "서버 응답 없음"으로 실패시킨다.</summary>
    public int FailReasons { get; set; }

    public Task<IReadOnlyList<Downtime>> GetDowntimesAsync(string equipCode, int limit) =>
        Task.FromResult<IReadOnlyList<Downtime>>(Downtimes.Where(d => d.EquipCode == equipCode).Take(limit).ToList());

    public Task<Downtime> ConfirmDowntimeReasonAsync(long downtimeId, ReasonRequest request)
    {
        ReasonsSent.Add((downtimeId, request));
        if (FailReasons > 0)
        {
            FailReasons--;
            return Task.FromException<Downtime>(new MesConnectionException("서버 응답이 없습니다.", new TimeoutException()));
        }
        var index = Downtimes.FindIndex(d => d.DowntimeId == downtimeId);
        Downtimes[index] = Downtimes[index] with
        {
            DtCat = request.DtCat, DtSub = request.DtSub, Note = request.Note, WorkerId = request.WorkerId, ReasonAt = DateTime.Now,
        };
        return Task.FromResult(Downtimes[index]);
    }
}

/// <summary>단말 파일 대신 메모리에 보관한다. Saves: 보관한 순서대로 (보내기 전에 적었는지 확인용)</summary>
public sealed class MemoryPendingReportStore : IPendingReportStore
{
    private readonly Dictionary<string, SavedReport> _byWorker = [];

    public List<SavedReport> Saves { get; } = [];

    public SavedReport? Load(string workerId) => _byWorker.GetValueOrDefault(workerId);

    public void Save(SavedReport report)
    {
        Saves.Add(report);
        _byWorker[report.WorkerId] = report;
    }

    public void Clear(string workerId) => _byWorker.Remove(workerId);
}

/// <summary>확인 창은 항상 "확인"을 누른 것으로 친다.</summary>
public sealed class AlwaysYesDialogs : IDialogService
{
    public bool Confirm(string message) => true;

    public string? AskText(string title, string prompt) => "수량 오입력";
}
