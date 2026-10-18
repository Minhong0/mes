-- 정지 사유 입력 테스트용 (H2). 설비 CNC01은 equipment-fixture.sql에 있다.
INSERT INTO worker (worker_id, worker_name, wc_code, role, use_yn) VALUES
    ('W1001', '김도윤', 'WC-100', 'OPERATOR', 'Y');

INSERT INTO common_code (group_code, code, code_name, parent_code, sort_order) VALUES
    ('DT_CAT', 'MATERIAL', '자재대기', NULL, 1),
    ('DT_CAT', 'BREAKDOWN', '설비고장', NULL, 4),
    ('DT_SUB', 'MAT_NOT_RECEIVED', '원자재 미입고', 'MATERIAL', 1),
    ('DT_SUB', 'BRK_SPINDLE', '스핀들 이상', 'BREAKDOWN', 3);
