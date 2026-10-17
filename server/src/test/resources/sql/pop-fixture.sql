-- POP(공정 작업) 테스트용 기준정보 (H2). 작업지시 품목·라우팅은 work-order-fixture.sql에 있다.
INSERT INTO process (process_code, process_name) VALUES
    ('OP-10', 'CNC 선삭'),
    ('OP-20', 'CNC 밀링'),
    ('OP-30', '열처리');

INSERT INTO equipment (equip_code, equip_name, wc_code, ideal_ct_min, planned_min_per_day, auto_collect, status) VALUES
    ('CNC01', 'CNC 선반 #1', 'WC-100', 12.00, 960, 'Y', 'RUN'),
    ('CNC03', 'CNC 밀링 #1', 'WC-200', 18.00, 960, 'Y', 'RUN');

INSERT INTO worker (worker_id, worker_name, wc_code, role, use_yn) VALUES
    ('W1001', '김도윤', 'WC-100', 'OPERATOR', 'Y'),
    ('W1099', '퇴사자', 'WC-100', 'OPERATOR', 'N');

INSERT INTO common_code (group_code, code, code_name, sort_order) VALUES
    ('DEFECT_TYPE', 'OVERSIZE', '치수 초과', 1),
    ('DEFECT_TYPE', 'SCRATCH', '표면 흠집', 2);
