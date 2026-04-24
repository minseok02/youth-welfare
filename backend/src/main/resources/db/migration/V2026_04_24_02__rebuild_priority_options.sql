-- priority_options 재설계
-- ONLINE, YOUTH_ONLY 제거 (추천 로직에서 already false)
-- EDU_JOB → EDUCATION, AMOUNT → FINANCE 코드 변경
-- JOB(일자리), PARTICIPATION(참여·기회), FAMILY(가족·돌봄) 신규 추가
-- user_priorities 참조가 있으므로 순서: 신규 코드 INSERT → 구 코드 삭제

-- 1. 구 코드를 참조하는 user_priorities 삭제 (ONLINE, YOUTH_ONLY)
DELETE up FROM user_priorities up
    JOIN priority_options po ON up.priority_option_id = po.id
WHERE po.code IN ('ONLINE', 'YOUTH_ONLY');

-- 2. EDU_JOB → EDUCATION 코드 변경
UPDATE priority_options SET code = 'EDUCATION', label = '교육·직업훈련' WHERE code = 'EDU_JOB';

-- 3. AMOUNT → FINANCE 코드 변경
UPDATE priority_options SET code = 'FINANCE', label = '금융·생활' WHERE code = 'AMOUNT';

-- 4. HOUSING, CULTURE, DEADLINE 라벨 정리
UPDATE priority_options SET label = '주거' WHERE code = 'HOUSING';
UPDATE priority_options SET label = '문화·여가' WHERE code = 'CULTURE';
UPDATE priority_options SET label = '마감임박' WHERE code = 'DEADLINE';

-- 5. ONLINE, YOUTH_ONLY 제거
DELETE FROM priority_options WHERE code IN ('ONLINE', 'YOUTH_ONLY');

-- 6. 신규 코드 추가
INSERT IGNORE INTO priority_options (code, label) VALUES
('JOB',           '일자리'),
('PARTICIPATION', '참여·기회'),
('FAMILY',        '가족·돌봄');
