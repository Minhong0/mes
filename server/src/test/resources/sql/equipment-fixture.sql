-- 수집기 테스트용 설비 1대 (H2). 실제 기준정보는 db/seed/01_master.sql에 있다.
INSERT INTO equipment (equip_code, equip_name, wc_code, ideal_ct_min, planned_min_per_day, auto_collect, status) VALUES
    ('CNC01', 'CNC 선반 1호기', 'WC-100', 12.00, 960, 'Y', 'RUN');
