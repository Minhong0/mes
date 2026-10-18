using MiniMes.Pop.Api;

namespace MiniMes.Pop.Storage;

/// <summary>
/// 결과를 모르는 실적 요청을 단말에 보관한다. 작업자마다 한 건.
///
/// 보내기 "전"에 적고, 저장됐는지(성공) 또는 저장되지 않았는지(서버 거절) 확실해지면 지운다.
/// 그래서 응답을 기다리다 POP가 꺼져도(정전, 강제 종료) 다음에 로그인하면 같은 요청을 다시 보낼 수 있다.
/// 같은 RequestId라 서버가 이미 저장했어도 한 번만 남는다.
/// </summary>
public interface IPendingReportStore
{
    SavedReport? Load(string workerId);

    void Save(SavedReport report);

    void Clear(string workerId);
}

/// <summary>보관한 실적 요청. 다시 보낼 공정(WoOpId)과, 화면에 보여 줄 작업 이름을 함께 둔다.</summary>
public sealed record SavedReport(string WorkerId, string WcCode, long WoOpId, string WoNo, string ProcessName,
    ReportRequest Request, DateTime SavedAt);
