-- LOT 테스트용 기준정보 (H2). work-order-fixture.sql(라우팅 3공정) 위에 산출 품목과 BOM을 더한다.
UPDATE item SET lot_yn = 'Y' WHERE item_code = 'RM-SCM-001';

INSERT INTO item (item_code, item_name, item_type, unit, lot_yn) VALUES
    ('SF-SFT-010', '1차 가공 샤프트', 'SF', 'EA', 'Y'),
    ('SF-SFT-020', '2차 정밀 샤프트', 'SF', 'EA', 'Y'),
    ('SF-SFT-030', '열처리 샤프트', 'SF', 'EA', 'Y'),
    ('RM-OIL-001', '절삭유', 'RM', 'L', 'N');

-- 선삭: 환봉 1개(LOT 관리) + 절삭유(소모품, LOT 관리 안 함)
INSERT INTO bom (parent_item_code, child_item_code, qty, unit, process_code) VALUES
    ('SF-SFT-010', 'RM-SCM-001', 1, 'EA', 'OP-10'),
    ('SF-SFT-010', 'RM-OIL-001', 0.025, 'L', 'OP-10'),
    ('SF-SFT-020', 'SF-SFT-010', 1, 'EA', 'OP-20'),
    ('SF-SFT-030', 'SF-SFT-020', 1, 'EA', 'OP-30');

-- 품질 보류·해제는 품질 담당(QC)·관리자만 한다
INSERT INTO worker (worker_id, worker_name, wc_code, role, use_yn) VALUES
    ('W6001', '강예린', NULL, 'QC', 'Y');
