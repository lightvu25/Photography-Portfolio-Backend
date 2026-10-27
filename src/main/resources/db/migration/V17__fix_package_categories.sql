-- V17: Ensure categories named "Gói ..." / slug "goi-..." are marked as PACKAGE
-- so they never appear on the homepage or public gallery, and switch thumbnail
-- transformations from c_fill to c_limit to preserve original aspect ratios.

UPDATE categories
SET type = 'PACKAGE'
WHERE type = 'PORTFOLIO'
  AND slug <> 'admin-noi-bo'
  AND slug <> 'feedback'
  AND (
    LOWER(slug) LIKE 'goi-%'
    OR LOWER(slug) LIKE 'package%'
    OR LOWER(name) LIKE 'gói%'
    OR LOWER(name) LIKE 'package%'
  );

UPDATE photos
SET thumbnail_url = REPLACE(thumbnail_url, 'w_400,c_fill,q_auto', 'w_600,c_limit,q_auto')
WHERE thumbnail_url LIKE '%w_400,c_fill,q_auto%';
