-- popup 컬럼 타입 정합 (2026-09-26, Supabase 에서 실행 완료)
-- description 이 varchar(255) 라 긴 설명이 value too long 으로 롤백됐고,
-- opening_hours 는 jsonb 에서 한 줄 텍스트("매일 11:00~20:00, 월 휴무")로 바꾼다.
ALTER TABLE popup
  ALTER COLUMN description     TYPE text,
  ALTER COLUMN title           TYPE varchar(500),
  ALTER COLUMN brand           TYPE varchar(500),
  ALTER COLUMN reservation_url TYPE varchar(2000);
ALTER TABLE popup ALTER COLUMN opening_hours TYPE text USING opening_hours::text;
-- 배열 원소도 varchar(255) 라 긴 URL 이 value too long 을 냈다.
ALTER TABLE popup
  ALTER COLUMN source_urls TYPE text[],
  ALTER COLUMN tags        TYPE text[],
  ALTER COLUMN image_urls  TYPE text[];
