#!/bin/bash
# docker compose의 db-init 컨테이너가 실행한다. MSSQL이 뜬 뒤 스키마와 초기 데이터를 넣는다.
#   - 테이블이 없으면(새 볼륨): db/ddl 전부 → db/seed
#   - 테이블이 있으면(이미 만든 DB): 없을 때만 추가하는 06번부터 다시 실행 (새로 생긴 DDL을 반영)
#   - 기준정보가 비어 있으면 seed를 넣는다 (지난번에 DDL만 하고 멈춘 경우)
# 몇 번을 다시 실행해도 결과가 같다.
set -euo pipefail

SQLCMD=(/opt/mssql-tools18/bin/sqlcmd -S "${DB_HOST:-mssql}" -U sa -P "$MSSQL_SA_PASSWORD" -C -I -b -f 65001)

run() {
    echo "  $(basename "$1")"
    "${SQLCMD[@]}" -i "$1" > /dev/null
}

query() {
    "${SQLCMD[@]}" -h -1 -W -Q "SET NOCOUNT ON; $1" | tr -d '[:space:]'
}

echo "MSSQL 접속 기다리는 중..."
for i in $(seq 1 60); do
    if "${SQLCMD[@]}" -Q "SELECT 1" > /dev/null 2>&1; then
        break
    fi
    if [ "$i" -eq 60 ]; then
        echo "MSSQL에 접속하지 못했습니다." >&2
        exit 1
    fi
    sleep 2
done

has_tables=$(query "SELECT CASE WHEN DB_ID('minimes') IS NOT NULL AND OBJECT_ID('minimes.dbo.item') IS NOT NULL THEN 1 ELSE 0 END")

if [ "$has_tables" = "0" ]; then
    echo "새 DB: 스키마를 만듭니다"
    for f in /db/ddl/*.sql; do run "$f"; done
else
    echo "이미 만든 DB: 추가된 스키마만 반영합니다"
    for f in /db/ddl/0[6-9]_*.sql /db/ddl/1[0-9]_*.sql; do run "$f"; done
fi

items=$(query "SELECT COUNT(*) FROM minimes.dbo.item")
if [ "$items" = "0" ]; then
    echo "초기 데이터를 넣습니다"
    for f in /db/seed/*.sql; do run "$f"; done
else
    echo "초기 데이터는 이미 있습니다 (품목 ${items}개)"
fi

echo "DB 준비 끝"
