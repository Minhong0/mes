-- 검사 테스트용 기준 (H2). 선삭(OP-10): 외경은 하한·상한, 표면 거칠기는 상한만
INSERT INTO inspection_spec (process_code, inspect_code, inspect_name, equip_code, unit, nominal, lsl, usl) VALUES
    ('OP-10', 'OD', '외경', NULL, 'mm', 38, 37.95, 38.05),
    ('OP-10', 'RA', '표면 거칠기', NULL, 'um', NULL, NULL, 3.2);
