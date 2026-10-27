-- 로그인 테스트용: pop-fixture.sql의 작업자(W1001, 퇴사자 W1099)와 lot-fixture.sql의 품질 담당(W6001)에게 비밀번호 1234
UPDATE worker SET password_hash = '$2a$10$GrsrNMiT8zo2wkT/49k.ruKGlhSnljM23Qh8FU0TB3Aw3dhHffX3G';
