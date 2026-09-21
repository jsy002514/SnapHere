-- SnapHere V4 — 시도 마스터 기준정보 적재 (PLC-001)
--
-- posts.area_code 가 regions 를 참조하므로 이 행이 없으면 게시글을 만들 수 없다.
-- 시도 코드는 TourAPI areaCode 를 그대로 쓴다 — 비연속(1~8, 31~39)이고 순번을 다시 매기지 않는다.
-- 행사 인증 반경 기본값은 여기서 정하지 않는다(null). 지역 운영 판단이 생길 때 UPDATE 로 넣고,
-- 그때까지는 2,000m 를 쓴다 (PLC-022).

insert into regions (area_code, name_ko, name_en) values
    ( 1, '서울',            'Seoul'),
    ( 2, '인천',            'Incheon'),
    ( 3, '대전',            'Daejeon'),
    ( 4, '대구',            'Daegu'),
    ( 5, '광주',            'Gwangju'),
    ( 6, '부산',            'Busan'),
    ( 7, '울산',            'Ulsan'),
    ( 8, '세종특별자치시',  'Sejong'),
    (31, '경기도',          'Gyeonggi-do'),
    (32, '강원특별자치도',  'Gangwon-do'),
    (33, '충청북도',        'Chungcheongbuk-do'),
    (34, '충청남도',        'Chungcheongnam-do'),
    (35, '경상북도',        'Gyeongsangbuk-do'),
    (36, '경상남도',        'Gyeongsangnam-do'),
    (37, '전북특별자치도',  'Jeonbuk-do'),
    (38, '전라남도',        'Jeollanam-do'),
    (39, '제주도',          'Jeju-do')
on conflict (area_code) do nothing;
