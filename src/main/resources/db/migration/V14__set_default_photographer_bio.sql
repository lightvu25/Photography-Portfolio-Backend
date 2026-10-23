-- V14: set the owner's official About bio when currently null or blank.
-- (Moved out of V12, which was already applied in production and must stay immutable.)
UPDATE site_settings
SET bio = 'Muốn tìm thợ chụp đẹp thì có rất nhiều, muốn tìm thợ đẹp zai, chụp đẹp thì hãy tìm đến anh',
    updated_at = CURRENT_TIMESTAMP
WHERE bio IS NULL OR BTRIM(bio) = '';
