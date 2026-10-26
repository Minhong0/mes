-- 공구 수명 관리. 이미 만든 DB에도 그대로 실행할 수 있게 없을 때만 추가한다.
USE minimes;
GO
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

-- 같은 공구의 알람을 찾을 때 (공구 하나를 설치한 동안 알람은 한 번)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'ix_equipment_alarm_tool')
    CREATE INDEX ix_equipment_alarm_tool ON equipment_alarm (tool_code, occurred_at);
GO

-- 이미 기준정보를 넣은 DB용. 빈 DB라면 설비가 아직 없어 넣지 않고, seed(01_master.sql)가 공구를 함께 넣는다.
-- 수명 근거는 data/master/README.md (2차 자료 10월 생산 수량 ÷ 공구 교체 횟수, 연삭석은 3차 자료 참고치)
IF EXISTS (SELECT 1 FROM equipment WHERE equip_code = 'CNC05') AND NOT EXISTS (SELECT 1 FROM tool)
    INSERT INTO tool (tool_code, tool_name, tool_type, equip_code, life_count) VALUES
        ('T-CNC01-INS', N'CNC01 선삭 인서트', 'INSERT', 'CNC01', 120),
        ('T-CNC02-INS', N'CNC02 선삭 인서트', 'INSERT', 'CNC02', 120),
        ('T-CNC03-EM', N'CNC03 키홈 엔드밀', 'ENDMILL', 'CNC03', 90),
        ('T-CNC04-EM', N'CNC04 키홈 엔드밀', 'ENDMILL', 'CNC04', 90),
        ('T-CNC05-WHL', N'CNC05 연삭석', 'WHEEL', 'CNC05', 200);
GO
