-- V15: reserved "Feedback" category. Photos assigned to it are the customer
-- feedback screenshots shown in the homepage / feedback-page marquee. They are
-- excluded from the public gallery (see PhotoRepository.PUBLIC_ONLY).
INSERT INTO categories (name, slug, sort_order, type)
VALUES ('Feedback', 'feedback', 998, 'FEEDBACK')
ON CONFLICT (slug) DO NOTHING;
