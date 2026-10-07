-- 기준정보 초기 데이터 
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO


INSERT INTO common_code_group (group_code, group_name) VALUES
    ('ITEM_TYPE', N'품목유형'),
    ('WO_STATUS', N'작업지시 상태'),
    ('EQUIP_STATUS', N'설비 상태'),
    ('DT_CAT', N'비가동 대분류'),
    ('DT_SUB', N'비가동 소분류'),
    ('DEFECT_TYPE', N'불량 유형');

-- common_code: 38건
INSERT INTO common_code (group_code, code, code_name, parent_code, sort_order) VALUES
    ('ITEM_TYPE', 'FG', N'완제품', NULL, 1),
    ('ITEM_TYPE', 'SF', N'반제품', NULL, 2),
    ('ITEM_TYPE', 'RM', N'원자재', NULL, 3),
    ('WO_STATUS', 'WAIT', N'대기', NULL, 1),
    ('WO_STATUS', 'RUN', N'진행', NULL, 2),
    ('WO_STATUS', 'DONE', N'완료', NULL, 3),
    ('WO_STATUS', 'CLOSED', N'마감', NULL, 4),
    ('EQUIP_STATUS', 'RUN', N'가동', NULL, 1),
    ('EQUIP_STATUS', 'IDLE', N'대기', NULL, 2),
    ('EQUIP_STATUS', 'SETUP', N'셋업', NULL, 3),
    ('EQUIP_STATUS', 'DOWN', N'정지', NULL, 4),
    ('DT_CAT', 'MATERIAL', N'자재대기', NULL, 1),
    ('DT_CAT', 'TOOL', N'공구교체', NULL, 2),
    ('DT_CAT', 'SETUP', N'품번교체(셋업)', NULL, 3),
    ('DT_CAT', 'BREAKDOWN', N'설비고장', NULL, 4),
    ('DT_CAT', 'QUALITY', N'품질문제', NULL, 5),
    ('DT_SUB', 'MAT_NOT_RECEIVED', N'원자재 미입고', 'MATERIAL', 1),
    ('DT_SUB', 'MAT_ISSUE_DELAY', N'자재 불출 지연', 'MATERIAL', 2),
    ('DT_SUB', 'MAT_PREV_PROCESS', N'전공정 대기', 'MATERIAL', 3),
    ('DT_SUB', 'TOOL_DRILL', N'드릴 교체', 'TOOL', 1),
    ('DT_SUB', 'TOOL_ENDMILL', N'엔드밀 교체', 'TOOL', 2),
    ('DT_SUB', 'TOOL_WHEEL', N'연삭석 교체', 'TOOL', 3),
    ('DT_SUB', 'TOOL_INSERT', N'인서트 교체', 'TOOL', 4),
    ('DT_SUB', 'SETUP_TOOL', N'공구 세팅', 'SETUP', 1),
    ('DT_SUB', 'SETUP_JIG', N'지그 교체', 'SETUP', 2),
    ('DT_SUB', 'SETUP_FIRST_ARTICLE', N'초도품 검사', 'SETUP', 3),
    ('DT_SUB', 'SETUP_PROGRAM', N'프로그램 변경', 'SETUP', 4),
    ('DT_SUB', 'BRK_BEARING', N'베어링 마모', 'BREAKDOWN', 1),
    ('DT_SUB', 'BRK_SENSOR', N'센서 불량', 'BREAKDOWN', 2),
    ('DT_SUB', 'BRK_SPINDLE', N'스핀들 이상', 'BREAKDOWN', 3),
    ('DT_SUB', 'BRK_HYDRAULIC', N'유압 누유', 'BREAKDOWN', 4),
    ('DT_SUB', 'BRK_ELECTRIC', N'전기 계통 이상', 'BREAKDOWN', 5),
    ('DT_SUB', 'QC_ANALYSIS', N'불량 원인 분석', 'QUALITY', 1),
    ('DT_SUB', 'QC_REWORK', N'재가공', 'QUALITY', 2),
    ('DT_SUB', 'QC_DIMENSION', N'치수 이탈 조치', 'QUALITY', 3),
    ('DEFECT_TYPE', 'OVERSIZE', N'치수 초과', NULL, 1),
    ('DEFECT_TYPE', 'SCRATCH', N'표면 흠집', NULL, 2),
    ('DEFECT_TYPE', 'LOW_HARDNESS', N'경도 미달', NULL, 3);

-- item: 10건
INSERT INTO item (item_code, item_name, item_type, spec, unit, note) VALUES
    ('FG-SFT-100', N'변속기 메인 샤프트 ASSY', 'FG', N'Ø35×L280mm', 'EA', N'최종 출하품'),
    ('SF-SFT-010', N'1차 가공 샤프트', 'SF', N'Ø38×L285mm', 'EA', N'OP-10 산출물'),
    ('SF-SFT-020', N'2차 정밀 샤프트', 'SF', N'Ø35.5×L282mm', 'EA', N'OP-20 산출물'),
    ('SF-SFT-030', N'열처리 샤프트', 'SF', N'Ø35.5×L282mm', 'EA', N'OP-30 산출물'),
    ('SF-SFT-040', N'연삭 샤프트', 'SF', N'Ø35×L280mm', 'EA', N'OP-40 산출물'),
    ('RM-SCM-001', N'SCM440 환봉', 'RM', N'Ø40×L300mm', 'EA', N'크롬몰리브덴강'),
    ('RM-KEY-001', N'키홈 핀', 'RM', N'Ø5×L15mm', 'EA', N'SUS304'),
    ('RM-OIL-001', N'절삭유', 'RM', NULL, 'L', N'수용성 소모품'),
    ('RM-GRD-001', N'연삭석', 'RM', N'Ø200mm', 'EA', N'소모성 공구 (약 200개 가공)'),
    ('RM-PKG-001', N'포장 트레이', 'RM', N'20구', 'EA', N'PP 소재');

-- process: 7건
INSERT INTO process (process_code, process_name, process_name_en, department) VALUES
    ('OP-10', N'CNC 선삭', 'Turning', N'생산1팀'),
    ('OP-20', N'CNC 밀링', 'Milling', N'생산1팀'),
    ('OP-30', N'열처리', 'Heat Treatment', N'생산2팀'),
    ('OP-40', N'연삭', 'Grinding', N'생산2팀'),
    ('OP-50', N'세척', 'Cleaning', N'생산2팀'),
    ('OP-60', N'최종 검사', 'Final Inspection', N'품질팀'),
    ('OP-70', N'조립 및 포장', 'Assembly & Packing', N'물류팀');

-- bom: 10건
INSERT INTO bom (parent_item_code, child_item_code, qty, unit, process_code) VALUES
    ('FG-SFT-100', 'SF-SFT-040', 1, 'EA', 'OP-70'),
    ('FG-SFT-100', 'RM-KEY-001', 1, 'EA', 'OP-70'),
    ('FG-SFT-100', 'RM-PKG-001', 0.05, 'EA', 'OP-70'),
    ('SF-SFT-040', 'SF-SFT-030', 1, 'EA', 'OP-40'),
    ('SF-SFT-040', 'RM-GRD-001', 0.005, 'EA', 'OP-40'),
    ('SF-SFT-030', 'SF-SFT-020', 1, 'EA', 'OP-30'),
    ('SF-SFT-020', 'SF-SFT-010', 1, 'EA', 'OP-20'),
    ('SF-SFT-020', 'RM-OIL-001', 0.025, 'L', 'OP-20'),
    ('SF-SFT-010', 'RM-SCM-001', 1, 'EA', 'OP-10'),
    ('SF-SFT-010', 'RM-OIL-001', 0.025, 'L', 'OP-10');

-- work_center: 7건
INSERT INTO work_center (wc_code, wc_name, process_code, location, workers, note) VALUES
    ('WC-100', N'선삭 작업장', 'OP-10', N'A동 1층', 4, N'2교대'),
    ('WC-200', N'밀링 작업장', 'OP-20', N'A동 1층', 4, N'2교대'),
    ('WC-300', N'열처리 작업장', 'OP-30', N'B동 1층', 2, N'2교대'),
    ('WC-400', N'연삭 작업장', 'OP-40', N'A동 2층', 2, N'2교대'),
    ('WC-500', N'세척 작업장', 'OP-50', N'A동 2층', 1, NULL),
    ('WC-600', N'검사 작업장', 'OP-60', N'B동 2층', 2, NULL),
    ('WC-700', N'조립/포장 작업장', 'OP-70', N'B동 1층', 2, NULL);

-- routing: 7건
INSERT INTO routing (item_code, seq, process_code, wc_code, output_item_code, std_time_min, time_basis, batch_max_qty, setup_time_min) VALUES
    ('FG-SFT-100', '10', 'OP-10', 'WC-100', 'SF-SFT-010', 12, 'EA', NULL, 30),
    ('FG-SFT-100', '20', 'OP-20', 'WC-200', 'SF-SFT-020', 18, 'EA', NULL, 45),
    ('FG-SFT-100', '30', 'OP-30', 'WC-300', 'SF-SFT-030', 480, 'BATCH', 200, 0),
    ('FG-SFT-100', '40', 'OP-40', 'WC-400', 'SF-SFT-040', 8, 'EA', NULL, 0),
    ('FG-SFT-100', '50', 'OP-50', 'WC-500', 'SF-SFT-040', 20, 'BATCH', 50, 0),
    ('FG-SFT-100', '60', 'OP-60', 'WC-600', 'SF-SFT-040', 5, 'EA', NULL, 0),
    ('FG-SFT-100', '70', 'OP-70', 'WC-700', 'FG-SFT-100', 3, 'EA', NULL, 0);

-- equipment: 12건
INSERT INTO equipment (equip_code, equip_name, wc_code, equip_type, maker, model, install_year, ideal_ct_min, planned_min_per_day, auto_collect, note) VALUES
    ('CNC01', N'CNC 선반 #1', 'WC-100', 'CNC_LATHE', 'DOOSAN', 'PUMA 2600', 2019, 12, 960, 'Y', N'주간 2교대 (8h×2)'),
    ('CNC02', N'CNC 선반 #2', 'WC-100', 'CNC_LATHE', 'DOOSAN', 'PUMA 2600', 2020, 12, 960, 'Y', N'주간 2교대 (8h×2)'),
    ('CNC03', N'CNC 밀링 #1', 'WC-200', 'CNC_MILL', 'HYUNDAI WIA', 'F500', 2018, 18, 960, 'Y', N'노후 설비'),
    ('CNC04', N'CNC 밀링 #2', 'WC-200', 'CNC_MILL', 'HYUNDAI WIA', 'F500D', 2021, 18, 960, 'Y', N'주간 2교대 (8h×2)'),
    ('CNC05', N'연삭기', 'WC-400', 'GRINDER', 'STUDER', 'S33', 2019, 8, 960, 'Y', N'주간 2교대 (8h×2)'),
    ('HTF-01', N'열처리로', 'WC-300', 'FURNACE', NULL, NULL, NULL, NULL, NULL, 'N', N'Batch 최대 200개 (외주 병행)'),
    ('USC-01', N'초음파 세척기', 'WC-500', 'WASHER', NULL, NULL, NULL, NULL, NULL, 'N', N'Batch 50개'),
    ('CMM-01', N'3차원 측정기', 'WC-600', 'MEASURE', NULL, NULL, NULL, NULL, NULL, 'N', NULL),
    ('HT-01', N'경도시험기', 'WC-600', 'MEASURE', NULL, NULL, NULL, NULL, NULL, 'N', NULL),
    ('SR-01', N'표면조도측정기', 'WC-600', 'MEASURE', NULL, NULL, NULL, NULL, NULL, 'N', NULL),
    ('HP-01', N'유압 프레스', 'WC-700', 'PRESS', NULL, NULL, NULL, NULL, NULL, 'N', N'키홈 핀 압입'),
    ('PKG-01', N'포장대', 'WC-700', 'PACKING', NULL, NULL, NULL, NULL, NULL, 'N', NULL);

-- inspection_spec: 14건
INSERT INTO inspection_spec (process_code, inspect_code, inspect_name, equip_code, unit, nominal, lsl, usl) VALUES
    ('OP-10', 'OD', N'외경', NULL, 'mm', 38, 37.95, 38.05),
    ('OP-10', 'RA', N'표면 거칠기', NULL, 'um', NULL, NULL, 3.2),
    ('OP-20', 'KW', N'키홈 폭', NULL, 'mm', 5.0, 4.98, 5.02),
    ('OP-20', 'PCD', N'스플라인 PCD', NULL, 'mm', 32, 31.97, 32.03),
    ('OP-30', 'HRC', N'표면 경도', 'HT-01', 'HRC', NULL, 58, 62),
    ('OP-30', 'CD', N'유효 경화 깊이', NULL, 'mm', NULL, 0.8, 1.2),
    ('OP-40', 'OD', N'외경', NULL, 'mm', 35, 34.99, 35.01),
    ('OP-40', 'RND', N'진원도', NULL, 'mm', NULL, NULL, 0.005),
    ('OP-40', 'RA', N'표면 거칠기', NULL, 'um', NULL, NULL, 0.8),
    ('OP-60', 'OD', N'외경', 'CMM-01', 'mm', 35, 34.99, 35.01),
    ('OP-60', 'KW', N'키홈 폭', 'CMM-01', 'mm', 5.0, 4.98, 5.02),
    ('OP-60', 'PCD', N'스플라인 PCD', 'CMM-01', 'mm', 32, 31.97, 32.03),
    ('OP-60', 'HRC', N'표면 경도', 'HT-01', 'HRC', NULL, 58, 62),
    ('OP-60', 'RA', N'표면 거칠기', 'SR-01', 'um', NULL, NULL, 0.8);
GO
