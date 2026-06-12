UPDATE image i
JOIN avatar a ON a.id = i.target_id
SET i.image_url = CASE a.name
    WHEN 'blue' THEN '/images/characters/blue.svg'
    WHEN 'green' THEN '/images/characters/green.svg'
    WHEN 'pink' THEN '/images/characters/pink.svg'
    WHEN 'purple' THEN '/images/characters/purple.svg'
    WHEN 'yellow' THEN '/images/characters/yellow.svg'
END
WHERE i.target_type = 'CHARACTER'
  AND i.sort_order = 0
  AND a.name IN ('blue', 'green', 'pink', 'purple', 'yellow');

UPDATE image i
JOIN avatar a ON a.id = i.target_id
SET i.image_url = CASE a.name
    WHEN 'blue' THEN '/images/characters/party-hat/blue.svg'
    WHEN 'green' THEN '/images/characters/party-hat/green.svg'
    WHEN 'pink' THEN '/images/characters/party-hat/pink.svg'
    WHEN 'purple' THEN '/images/characters/party-hat/purple.svg'
    WHEN 'yellow' THEN '/images/characters/party-hat/yellow.svg'
END
WHERE i.target_type = 'CHARACTER'
  AND i.sort_order = 2
  AND a.name IN ('blue', 'green', 'pink', 'purple', 'yellow');
