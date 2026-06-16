UPDATE image i
JOIN avatar a ON a.id = i.target_id
SET i.image_url = CONCAT('/images/characters/', a.name, '.png')
WHERE i.target_type = 'CHARACTER'
  AND i.sort_order = 0
  AND a.name IN ('blue', 'green', 'pink', 'purple', 'yellow');

UPDATE image i
JOIN avatar a ON a.id = i.target_id
SET i.image_url = CONCAT('/images/characters/party-hat/', a.name, '.png')
WHERE i.target_type = 'CHARACTER'
  AND i.sort_order = 2
  AND a.name IN ('blue', 'green', 'pink', 'purple', 'yellow');
