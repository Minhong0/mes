# POP (현장 작업자 단말)

C# .NET 8, WPF, MVVM(CommunityToolkit.Mvvm). 작업자가 작업장의 작업을 골라 시작하고, 양품·불량 실적을 등록하고, 공정을 완료합니다.

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
  └─ 고른 작업: [작업 시작] → 양품·불량 입력 → [실적 등록] (여러 번) → [공정 완료]
                등록한 실적은 [취소]할 수 있다 (사유 입력, 취소 표시만 하고 지우지 않음)
```

## 통신 오류 처리

실적을 보냈는데 응답이 없으면(서버 꺼짐, 5초 초과) 저장됐는지 알 수 없습니다. 그래서:

- 보낸 요청(요청 ID 포함)을 화면에 잡아 두고 입력을 잠급니다. 버튼이 **[다시 보내기]**로 바뀝니다.
- 다시 보내면 같은 요청 ID가 가므로, 처음 요청이 이미 저장됐어도 서버가 한 번만 저장합니다.
- 서버가 입력 오류로 거절한 경우(400 등)는 저장되지 않은 것이 확실하므로 잡아 두지 않습니다.

## 구조

| 폴더 | 내용 |
| --- | --- |
| `Api/` | `IMesApi`(화면이 쓰는 서버 기능), `MesApiClient`(HttpClient 구현), 요청·응답 record |
| `ViewModels/` | `MainViewModel`(화면 전환), `LoginViewModel`, `WorkViewModel`, `DefectEntry`, `IDialogService` |
| `Views/` | `LoginView`, `WorkView`, 입력 창, `WpfDialogService` |

## 테스트

`MiniMes.Pop.Tests` (xUnit): 가짜 서버(`FakeMesApi`)로 ViewModel을 시험합니다. 창을 띄우지 않습니다.

```bash
dotnet test pop/MiniMes.Pop.Tests
```
