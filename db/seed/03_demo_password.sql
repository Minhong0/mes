-- 시연용 비밀번호: 모든 작업자 1234 (BCrypt 해시). 실제 운영에서는 넣지 않는다.
-- 비밀번호가 아직 없는 작업자에게만 넣는다 (바꾼 비밀번호는 그대로 둔다).
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

UPDATE worker SET password_hash = '$2a$10$GrsrNMiT8zo2wkT/49k.ruKGlhSnljM23Qh8FU0TB3Aw3dhHffX3G'
WHERE password_hash IS NULL;
GO
