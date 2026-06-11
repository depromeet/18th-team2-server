-- 주최자(꼬깔모자) 캐릭터 이미지 시드. sort_order=2 는 주최자 전용 변형 이미지를 의미한다.
insert into image (id, image_url, target_type, target_id, sort_order, created_at, updated_at)
values
    (14, '/images/characters/Property 1=Default.png', 'CHARACTER', 1, 2, current_timestamp(6), current_timestamp(6)),
    (15, '/images/characters/Property 1=Girl.png', 'CHARACTER', 2, 2, current_timestamp(6), current_timestamp(6)),
    (16, '/images/characters/Property 1=Choco.png', 'CHARACTER', 3, 2, current_timestamp(6), current_timestamp(6)),
    (17, '/images/characters/Property 1=Cloud.png', 'CHARACTER', 4, 2, current_timestamp(6), current_timestamp(6)),
    (18, '/images/characters/Property 1=Candle.png', 'CHARACTER', 5, 2, current_timestamp(6), current_timestamp(6));
