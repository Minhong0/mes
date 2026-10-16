namespace MiniMes.Simulator;

/// <summary>
/// 설비가 MQTT로 보내는 메시지 한 건. 서버의 equipment_data 테이블 한 행과 필드가 1:1로 대응한다.
/// JSON 예: {"messageId":"CNC01-261010090000-000001","equipCode":"CNC01","collectedAt":"2026-10-10T09:00:00.123+09:00",
///          "cycleCount":1,"status":"RUN","spindleVibration":1.21,"spindleTemp":35.4}
/// 정지 중에는 사유가 붙는다: ..."status":"DOWN",...,"downtimeCategory":"BREAKDOWN","downtimeReason":"BRK_SPINDLE"}
/// </summary>
/// <param name="MessageId">메시지 고유 번호. 재전송으로 같은 메시지가 두 번 와도 서버가 한 번만 저장하는 기준.</param>
/// <param name="CollectedAt">설비에서 측정한 시각. 공장 시계(시뮬레이션 시각) 기준이라 배속이면 현실 시각보다 앞서 간다.</param>
/// <param name="CycleCount">가공 완료 누적 개수. 서버는 이전 값과의 차이로 생산 수량을 계산한다.</param>
/// <param name="Status">RUN(가동), IDLE(대기), SETUP(셋업), DOWN(정지). 공통코드 EQUIP_STATUS와 같은 값.</param>
/// <param name="DowntimeCategory">정지 중일 때만: 비가동 대분류 코드 (공통코드 DT_CAT). 가동 중이면 null이고 JSON에서 빠진다.</param>
/// <param name="DowntimeReason">정지 중일 때만: 비가동 소분류 코드 (공통코드 DT_SUB).</param>
public record EquipmentMessage(
    string MessageId,
    string EquipCode,
    DateTimeOffset CollectedAt,
    int CycleCount,
    string Status,
    double SpindleVibration,
    double SpindleTemp,
    string? DowntimeCategory,
    string? DowntimeReason);
