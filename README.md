# Mini-MES

자동차 변속기 샤프트 정밀가공 공장(가상 기업, 스마트팩토리 부트캠프 시나리오)을 위한 MES 포트폴리오 프로젝트입니다.
현장은 C#(.NET), 서버는 Java Spring으로 나누어 구현합니다.

> 작성 중입니다. 기능, 실행 방법, 시연 영상은 개발이 진행되면서 채워집니다.

## 구성

| 폴더 | 내용 | 기술 |
| --- | --- | --- |
| `server/` | MES 서버 (기준정보, 작업지시, 실적, LOT 추적, OEE) | Java 21, Spring Boot 4.0, JPA, MyBatis, Thymeleaf |
| `pop/` | 현장 작업자용 POP 단말 | C# .NET 8, WPF (MVVM) |
| `simulator/` | 설비(CNC01~05) 데이터 시뮬레이터 | C# .NET 8, MQTTnet |
| `tools/` | 부트캠프 엑셀을 seed SQL로 변환하는 스크립트 | Python, openpyxl |
| `data/master/` | 기준정보 CSV (품목, BOM, 라우팅, 작업장, 설비) | CSV |
| `db/` | DDL, seed SQL | MSSQL |
| `infra/` | MQTT 브로커 설정 | Mosquitto |
| `docs/` | 기획 문서, 기술 결정 기록 | Markdown |

## 실행 방법 (개발 중)

```bash
# 1. 인프라 (MSSQL, MQTT 브로커)
cp .env.example .env
docker compose up -d

# 2. 서버 (http://localhost:8080)
cd server && ./gradlew bootRun

# 3. 현장 프로그램 (POP, 설비 시뮬레이터)
dotnet build MiniMes.sln
```

## 기술 결정

[docs/decisions.md](docs/decisions.md) 참고.
