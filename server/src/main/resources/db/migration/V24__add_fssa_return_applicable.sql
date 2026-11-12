-- Whether a shop must file the FSSAI annual return (Form D-1). Derived from the
-- FSSAI Kind of Business (KOB): Manufacturer (including Repacker/Relabeller),
-- Importer, Exporter-Manufacturer and 100% EOU units are liable; Merchant
-- Exporter and trade/retail/food-service KOBs are not. When no KOB is stored
-- the shop category is used as a fallback.
ALTER TABLE shops
    ADD COLUMN IF NOT EXISTS fssa_return_applicable BOOLEAN NOT NULL DEFAULT FALSE;

-- Authoritative: latest FSSAI document's KOB.
UPDATE shops s
SET fssa_return_applicable = TRUE
WHERE lower(COALESCE((
        SELECT d.kind_of_business
        FROM documents d
        WHERE d.shop_id = s.id AND d.document_type = 'FSSAI_FOOD_LICENSE'
        ORDER BY d.version DESC, d.id DESC
        LIMIT 1
    ), '')) ~ '(manufactur|repack|relabel|importer|imports)'
  AND lower(COALESCE((
        SELECT d.kind_of_business
        FROM documents d
        WHERE d.shop_id = s.id AND d.document_type = 'FSSAI_FOOD_LICENSE'
        ORDER BY d.version DESC, d.id DESC
        LIMIT 1
    ), '')) NOT LIKE '%merchant export%';

-- Fallback to the shop category only when there is no usable KOB on file.
UPDATE shops s
SET fssa_return_applicable = TRUE
WHERE NOT EXISTS (
        SELECT 1 FROM documents d
        WHERE d.shop_id = s.id
          AND d.document_type = 'FSSAI_FOOD_LICENSE'
          AND d.kind_of_business IS NOT NULL
          AND btrim(d.kind_of_business) <> ''
    )
  AND (
        lower(s.category) LIKE '%food manufactur%'
        OR lower(s.category) LIKE '%food processing%'
        OR lower(s.category) LIKE '%import%'
        OR lower(s.category) LIKE '%export%'
        OR lower(s.category) LIKE '%repack%'
        OR lower(s.category) LIKE '%relabel%'
  );
