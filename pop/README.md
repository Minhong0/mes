# POP (현장 작업자 단말)

C# .NET 8, WPF, MVVM(CommunityToolkit.Mvvm). 작업자가 작업장의 작업을 골라 시작하고, 양품·불량 실적을 등록하고, 공정을 완료합니다. 설비가 멈춘 이유(정지 사유)도 확정합니다.

## 실행

1. 서버를 먼저 띄웁니다 ([server/README.md](../server/README.md)).
2. Visual Studio에서 `MiniMes.sln`을 열고 `MiniMes.Pop`을 시작 프로젝트로 실행합니다. 또는 저장소 루트에서 `dotnet run --project pop/MiniMes.Pop`.
3. 사번으로 로그인합니다: `W1001`(선삭), `W2001`(밀링), `W4001`(연삭), `W9001`(관리자, 작업장 선택 가능).

서버 주소는 환경 변수 `MES_URL`로 바꿀 수 있습니다 (기본 `http://localhost:8080/`).

## 화면 흐름

```
로그인(사번) → 작업 화면
  ├─ 작업장·설비 선택 (소속 작업장, 그 작업장의 첫 설비가 기본)
  ├─ 작업 목록: 대기·진행 중인 공정 (진행 중이 먼저, 그다음 납기 순)
  ├─ [작업] 탭
  │    고른 작업: [작업 시작] → 양품·불량 입력 → 투입 LOT 확인 → [실적 등록] (여러 번) → [공정 완료]
  │    등록한 실적은 [취소]할 수 있다 (사유 입력, 취소 표시만 하고 지우지 않음)
  └─ [설비 정지] 탭 (머리에 미확인 정지 수)
       고른 설비의 최근 정지 10건 → 정지를 고르고 대분류·소분류·메모 → [사유 저장]
```

투입 LOT: 수량(양품 + 불량)을 넣으면 먼저 들어온 LOT부터 필요한 만큼 자동으로 체크됩니다. 다른 LOT을 썼으면 체크를 바꿉니다. 모자라면 부족 수량이 빨갛게 보이고 등록할 수 없습니다. 등록하면 양품으로 만든 LOT 번호가 나옵니다.

등록한 불량은 서버의 일별 집계에 들어가 OEE 품질이 됩니다. 확정한 정지 사유는 모니터링 화면과 정지 사유 파레토에 바로 나옵니다.

## 통신 오류 처리

실적을 보냈는데 응답이 없으면(서버 꺼짐, 5초 초과) 저장됐는지 알 수 없습니다. 그래서:

- 보낸 요청(요청 ID 포함)을 화면에 잡아 두고 입력을 잠급니다. 버튼이 **[다시 보내기]**로 바뀝니다.
- 다시 보내면 같은 요청 ID가 가므로, 처음 요청이 이미 저장됐어도 서버가 한 번만 저장합니다.
- 서버가 입력 오류로 거절한 경우(400 등)는 저장되지 않은 것이 확실하므로 잡아 두지 않습니다.
- 실적 요청은 **보내기 전에** 단말 파일 `%LOCALAPPDATA%\MiniMes.Pop\pending\{사번}.json`에 적고, 결과가 확실해지면(저장됨, 거절됨) 지웁니다. 응답을 기다리다 POP가 꺼져도 다음에 로그인하면 그 요청을 같은 요청 ID로 자동으로 다시 보냅니다.
- 정지 사유 저장은 같은 내용을 다시 보내도 결과가 같아서(PUT), 응답이 없으면 [사유 저장]을 다시 누르면 됩니다.

## 구조

| 폴더 | 내용 |
| --- | --- |
| `Api/` | `IMesApi`(화면이 쓰는 서버 기능), `MesApiClient`(HttpClient 구현), 요청·응답 record |
| `ViewModels/` | `MainViewModel`(화면 전환), `LoginViewModel`, `WorkViewModel`, `DowntimeViewModel`(설비 정지 탭), `InputLotGroup`(투입 LOT), `DefectEntry`, `IDialogService` |
| `Views/` | `LoginView`, `WorkView`, `DowntimeView`, 입력 창, `WpfDialogService` |
| `Storage/` | `IPendingReportStore`, `FilePendingReportStore` (보내지 못한 실적을 단말 파일에 보관) |

## 테스트

`MiniMes.Pop.Tests` (xUnit): 가짜 서버(`FakeMesApi`)로 ViewModel을 시험합니다. 창을 띄우지 않습니다.

```bash
dotnet test pop/MiniMes.Pop.Tests
```
