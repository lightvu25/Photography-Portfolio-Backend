-- Reserved admin-only photo category. PACKAGE-type categories never appear in
-- the public category list, and photos whose categories are ALL PACKAGE-type
-- are excluded from the public gallery (see PhotoRepository.PUBLIC_ONLY).
-- Slug 'admin-noi-bo' is protected from deletion in CategoryService.
INSERT INTO categories (name, slug, sort_order, type)
VALUES ('Nội bộ (Admin)', 'admin-noi-bo', 999, 'PACKAGE')
ON CONFLICT (slug) DO NOTHING;
