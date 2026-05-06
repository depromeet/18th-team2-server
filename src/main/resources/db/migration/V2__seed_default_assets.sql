insert into avatar (id, name, created_at, updated_at)
values
    (1, 'Default', current_timestamp(6), current_timestamp(6)),
    (2, 'Girl', current_timestamp(6), current_timestamp(6)),
    (3, 'Choco', current_timestamp(6), current_timestamp(6)),
    (4, 'Cloud', current_timestamp(6), current_timestamp(6)),
    (5, 'Candle', current_timestamp(6), current_timestamp(6));

insert into rolling_paper_wrapper (id, name, created_at, updated_at)
values
    (1, 'Topping_Candle', current_timestamp(6), current_timestamp(6)),
    (2, 'Topping_Cherry', current_timestamp(6), current_timestamp(6)),
    (3, 'Topping_Strawberry', current_timestamp(6), current_timestamp(6));

insert into image (id, image_url, target_type, target_id, sort_order, created_at, updated_at)
values
    (1, '/images/characters/Type=Default, Shape=Default.png', 'CHARACTER', 1, 0, current_timestamp(6), current_timestamp(6)),
    (2, '/images/character-thumbnails/Type=Default, Shape=Circle.png', 'CHARACTER', 1, 1, current_timestamp(6), current_timestamp(6)),
    (3, '/images/characters/Type=Girl, Shape=Default.png', 'CHARACTER', 2, 0, current_timestamp(6), current_timestamp(6)),
    (4, '/images/character-thumbnails/Type=Girl, Shape=Circle.png', 'CHARACTER', 2, 1, current_timestamp(6), current_timestamp(6)),
    (5, '/images/characters/Type=Choco, Shape=Default.png', 'CHARACTER', 3, 0, current_timestamp(6), current_timestamp(6)),
    (6, '/images/character-thumbnails/Type=Choco, Shape=Circle.png', 'CHARACTER', 3, 1, current_timestamp(6), current_timestamp(6)),
    (7, '/images/characters/Type=Cloud, Shape=Default.png', 'CHARACTER', 4, 0, current_timestamp(6), current_timestamp(6)),
    (8, '/images/character-thumbnails/Type=Cloud, Shape=Circle.png', 'CHARACTER', 4, 1, current_timestamp(6), current_timestamp(6)),
    (9, '/images/characters/Type=Candle, Shape=Default.png', 'CHARACTER', 5, 0, current_timestamp(6), current_timestamp(6)),
    (10, '/images/character-thumbnails/Type=Candle, Shape=Circle.png', 'CHARACTER', 5, 1, current_timestamp(6), current_timestamp(6)),
    (11, '/images/rolling-paper-wrappers/Topping_Candle.svg', 'ROLLING_PAPER_WRAPPER', 1, 0, current_timestamp(6), current_timestamp(6)),
    (12, '/images/rolling-paper-wrappers/Topping_Cherry.svg', 'ROLLING_PAPER_WRAPPER', 2, 0, current_timestamp(6), current_timestamp(6)),
    (13, '/images/rolling-paper-wrappers/Topping_Strawberry.svg', 'ROLLING_PAPER_WRAPPER', 3, 0, current_timestamp(6), current_timestamp(6));
