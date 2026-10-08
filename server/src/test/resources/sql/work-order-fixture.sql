-- 작업지시 테스트용 최소 기준정보 (H2). 실제 기준정보는 db/seed/01_master.sql에 있다.
INSERT INTO item (item_code, item_name, item_type, spec, unit) VALUES
    ('FG-SFT-100', '변속기 메인 샤프트 ASSY', 'FG', 'Ø35×L280mm', 'EA'),
    ('RM-SCM-001', 'SCM440 환봉', 'RM', 'Ø40×L300mm', 'EA');

INSERT INTO routing (item_code, seq, process_code, wc_code, output_item_code, std_time_min, time_basis, batch_max_qty, setup_time_min) VALUES
    ('FG-SFT-100', 10, 'OP-10', 'WC-100', 'SF-SFT-010', 12, 'EA', NULL, 30),
    ('FG-SFT-100', 20, 'OP-20', 'WC-200', 'SF-SFT-020', 18, 'EA', NULL, 45),
    ('FG-SFT-100', 30, 'OP-30', 'WC-300', 'SF-SFT-030', 480, 'BATCH', 200, 0);
