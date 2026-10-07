# DB 스크립트

| 폴더 | 내용 |
| --- | --- |
| `ddl/` | 테이블과 뷰 생성 스크립트. 파일 번호 순서대로 실행합니다. |
| `seed/` | 초기 데이터 (기준정보, 부트캠프 과거 실적). `tools/`의 스크립트로 생성합니다. |

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
