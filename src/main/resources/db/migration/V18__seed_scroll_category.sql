-- V18: reserved "Scroll" category. Photos assigned to it are displayed
-- exclusively in the homepage scroll-driven hero section and excluded from
-- the public gallery and other portfolio sections.
INSERT INTO categories (name, slug, sort_order, type)
VALUES ('Scroll', 'scroll', 997, 'SCROLL')
ON CONFLICT (slug) DO UPDATE SET type = 'SCROLL';
