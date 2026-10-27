-- 로그인: 작업자 비밀번호 (BCrypt 해시). 이미 만든 DB에도 그대로 실행할 수 있게 없을 때만 추가한다.
-- 시연용 비밀번호는 db/seed/03_demo_password.sql이 넣는다 (DDL에는 비밀번호를 두지 않는다).
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF COL_LENGTH('worker', 'password_hash') IS NULL
    ALTER TABLE worker ADD password_hash VARCHAR(100) NULL;   -- NULL이면 로그인할 수 없다
GO
