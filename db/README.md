# DB 스크립트

| 폴더 | 내용 |
| --- | --- |
| `ddl/` | 테이블과 뷰 생성 스크립트. 파일 번호 순서대로 실행합니다. |
| `seed/` | 초기 데이터 (기준정보, 부트캠프 과거 실적). `tools/`의 스크립트로 생성합니다. |
| `scripts/` | 운영 보조 스크립트. `reset_collected.sql`: 시뮬레이터·수집기가 만든 데이터만 지웁니다. `add_workers.sql`: 이미 만든 DB에 POP 작업자를 넣습니다. |

ERD와 설계 이유는 [docs/erd.md](../docs/erd.md)에 있습니다.

## 수동 실행 방법

`docker compose up -d`로 MSSQL 컨테이너를 띄운 뒤, 저장소 루트에서 실행합니다 (Git Bash 기준).

```bash
source .env
export MSYS_NO_PATHCONV=1   # Git Bash의 경로 자동 변환 끄기
docker exec minimes-mssql mkdir -p /tmp/ddl
docker cp db/ddl/. minimes-mssql:/tmp/ddl/
for f in $(ls db/ddl/*.sql | xargs -n1 basename); do
  docker exec minimes-mssql /opt/mssql-tools18/bin/sqlcmd \
    -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -I -b -f 65001 -i /tmp/ddl/$f
done
```

- `-I`: sqlcmd는 기본으로 `QUOTED_IDENTIFIER`가 꺼져 있어, 계산 컬럼(`production_daily.run_min`)이 있는 테이블에 쓰기가 실패합니다. 스크립트 안에도 `SET QUOTED_IDENTIFIER ON`을 넣어 두었습니다.
- `-f 65001`: 한글 주석과 데이터를 UTF-8로 읽습니다.
- 처음부터 다시 만들려면 먼저 `DROP DATABASE minimes`를 실행합니다.
- `06_equipment_limit.sql`, `07_work_result_request.sql`, `08_downtime_reason.sql`, `09_lot_tracking.sql`, `10_routing_lot_size.sql`, `11_inspection.sql`은 없을 때만 만들거나 추가하므로, 이미 만든 DB에 이 파일들만 따로 실행해도 됩니다.

## 초기 데이터 (seed)

```bash
python tools/import_excel.py          # data/master CSV + 2차 엑셀 → db/seed/*.sql (검증 포함)
docker exec minimes-mssql mkdir -p /tmp/seed
docker cp db/seed/. minimes-mssql:/tmp/seed/
for f in 01_master.sql 02_history.sql; do
  docker exec minimes-mssql /opt/mssql-tools18/bin/sqlcmd \
    -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -I -b -f 65001 -i /tmp/seed/$f
done
```

| 파일 | 내용 | 다시 실행 |
| --- | --- | --- |
| `seed/01_master.sql` | 기준정보 (공통코드 38, 품목 10, BOM 10, 공정 7, 작업장 7, 라우팅 7, 설비 12, 검사 규격 14, 작업자 7) | 빈 DB에서 한 번만 |
| `seed/02_history.sql` | 부트캠프 2025년 10월 실적 (일별 실적 115, 비가동 220) | 가능 (기존 BOOTCAMP 데이터를 지우고 다시 넣음) |

seed 파일은 자동 생성 파일이므로 직접 고치지 말고, CSV나 스크립트를 고친 뒤 다시 생성합니다.

## 수집 데이터 초기화

시뮬레이터로 쌓은 데이터(`equipment_data`, 상태 이력, `EQUIP` 비가동, `MES` 생산 실적, 관리한계 알람)만 지웁니다. 기준정보, 부트캠프 실적, 관리한계(`equipment_limit`)는 남습니다. 서버를 끈 뒤 실행합니다.

```bash
source .env
export MSYS_NO_PATHCONV=1
docker cp db/scripts/reset_collected.sql minimes-mssql:/tmp/
docker exec minimes-mssql /opt/mssql-tools18/bin/sqlcmd   -S localhost -U sa -P "$MSSQL_SA_PASSWORD" -C -I -b -f 65001 -i /tmp/reset_collected.sql
```

공장 시계도 처음부터 다시 시작하려면 시뮬레이터 실행 폴더의 `outbox.db`도 지웁니다 (못 보낸 메시지가 없을 때).
