-- 포장 분할 테스트용 기준정보 (H2): 공정 하나(OP-70 조립·포장)짜리 품목. 산출 LOT은 20개씩.
INSERT INTO item (item_code, item_name, item_type, unit, lot_yn) VALUES
    ('FG-PACK', '포장 시험품', 'FG', 'EA', 'Y'),
    ('RM-BAR', '시험 환봉', 'RM', 'EA', 'Y');

INSERT INTO routing (item_code, seq, process_code, wc_code, output_item_code, std_time_min, time_basis, setup_time_min, lot_size) VALUES
    ('FG-PACK', 70, 'OP-70', 'WC-700', 'FG-PACK', 3, 'EA', 0, 20);

INSERT INTO bom (parent_item_code, child_item_code, qty, unit, process_code) VALUES
    ('FG-PACK', 'RM-BAR', 1, 'EA', 'OP-70');
