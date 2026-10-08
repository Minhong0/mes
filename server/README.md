# MES 서버 (Spring Boot)

Java 21, Spring Boot 4.0, JPA + MyBatis, MSSQL.

## IntelliJ에서 열기

1. IntelliJ에서 **File → Open**을 누르고 `MES/server` 폴더(또는 `server/build.gradle`)를 선택합니다. **Open as Project**로 엽니다.
2. 오른쪽 아래에 Gradle 프로젝트를 불러오는 진행 표시가 끝날 때까지 기다립니다.
3. **Settings → Build, Execution, Deployment → Build Tools → Gradle**에서 Gradle JVM이 Java 21인지 확인합니다.
4. Lombok 플러그인은 IntelliJ에 기본으로 들어 있습니다. 처음 열 때 "Enable annotation processing" 알림이 뜨면 허용합니다.

## 실행

1. 저장소 루트에서 `docker compose up -d`로 MSSQL을 띄웁니다. DB와 초기 데이터는 [db/README.md](../db/README.md)를 참고하세요.
2. `MesServerApplication`의 ▶ 버튼으로 실행합니다. DB 비밀번호는 저장소 루트의 `.env`에서 자동으로 읽습니다.
3. http://localhost:8080/swagger-ui.html 에서 API를 확인하고 직접 호출해 볼 수 있습니다.

## 테스트

| 명령 | 내용 | Docker |
| --- | --- | --- |
| `./gradlew test` | 단위 테스트 + H2 메모리 DB 테스트 | 필요 없음 |
| `./gradlew integrationTest` | MSSQL 컨테이너에 `db/ddl`, `db/seed`를 적용한 뒤 API 호출 | 필요 |

IntelliJ에서는 테스트 클래스 옆의 ▶ 버튼으로 하나씩 실행할 수 있습니다.

## 패키지 구조

기능(도메인) 단위로 나누고, 각 기능 안에서 계층별로 나눴습니다.

```
com.minimes
├─ common
│  ├─ config      Clock 등 공통 설정
│  └─ error       ErrorCode, BusinessException, 공통 오류 응답, 전역 예외 처리
├─ master         기준정보 (조회)
│  ├─ domain      엔티티: Item, Routing, Equipment, CommonCode ...
│  ├─ repository  Spring Data JPA
│  ├─ mapper      MyBatis (BOM 재귀 쿼리) → resources/mapper/master/BomMapper.xml
│  ├─ service
│  └─ web         REST 컨트롤러, 응답 DTO
└─ workorder      작업지시 (생성, 상태 변경)
   ├─ domain      WorkOrder(상태 규칙), WorkOrderOperation
   ├─ repository
   ├─ service
   └─ web
```

## API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/items?type=FG` | 품목 목록 (type 생략 가능) |
| GET | `/api/items/{itemCode}` | 품목 단건 |
| GET | `/api/items/{itemCode}/routings` | 라우팅 |
| GET | `/api/items/{itemCode}/bom-tree` | BOM 전개 |
| GET | `/api/equipment` | 설비 목록 |
| GET | `/api/common-codes/{groupCode}` | 공통코드 |
| POST | `/api/work-orders` | 작업지시 생성 |
| GET | `/api/work-orders?status=RUN` | 작업지시 목록 |
| GET | `/api/work-orders/{woNo}` | 작업지시 단건 |
| POST | `/api/work-orders/{woNo}/start` | 시작 (대기 → 진행) |
| POST | `/api/work-orders/{woNo}/complete` | 완료 (진행 → 완료) |
| POST | `/api/work-orders/{woNo}/close` | 마감 (완료 → 마감) |

오류 응답은 항상 같은 형식입니다.

```json
{"code": "INVALID_STATUS_TRANSITION", "message": "WAIT 상태의 작업지시는 마감할 수 없습니다. (DONE 상태에서만 가능)", "fieldErrors": []}
```
