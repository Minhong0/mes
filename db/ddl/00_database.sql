-- Mini-MES 데이터베이스 생성
-- 한글 정렬·비교를 위해 Korean_100_CI_AS 콜레이션 사용
IF DB_ID(N'minimes') IS NULL
    CREATE DATABASE minimes COLLATE Korean_100_CI_AS;
GO
