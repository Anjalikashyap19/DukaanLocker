-- V20: Ensure payment_transactions.document_type is INTEGER (ordinal) for
-- @Enumerated(EnumType.ORDINAL) mapping on PaymentTransaction entity.
--
-- DocumentType ordinal mapping (by declaration order in DocumentType.java):
--   0=GST, 1=PAN, 2=SHOP_ESTABLISHMENT, 3=TRADE_LICENSE, 4=MSME_CERTIFICATE,
--   5=PROFESSIONAL_TAX, 6=TRADEMARK, 7=PROPERTY_TAX, 8=IEC, 9=POLLUTION_CONTROL,
--  10=FIRE_SAFETY, 11=LABOUR_LICENSE, 12=SHOP_INSURANCE, 13=DRUG_LICENSE,
--  14=FSSAI_FOOD_LICENSE, 15=AADHAAR, 16=CUSTOM

DO $$
DECLARE
  col_type text;
BEGIN
  SELECT data_type INTO col_type
  FROM information_schema.columns
  WHERE table_name = 'payment_transactions' AND column_name = 'document_type';

  IF col_type = 'character varying' THEN
    -- Column is still VARCHAR: backfill ordinals then convert
    ALTER TABLE payment_transactions ADD COLUMN document_type_ordinal INTEGER;

    UPDATE payment_transactions SET document_type_ordinal = CASE document_type
        WHEN 'GST'                THEN 0
        WHEN 'PAN'                THEN 1
        WHEN 'SHOP_ESTABLISHMENT' THEN 2
        WHEN 'TRADE_LICENSE'      THEN 3
        WHEN 'MSME_CERTIFICATE'   THEN 4
        WHEN 'PROFESSIONAL_TAX'   THEN 5
        WHEN 'TRADEMARK'          THEN 6
        WHEN 'PROPERTY_TAX'       THEN 7
        WHEN 'IEC'                THEN 8
        WHEN 'POLLUTION_CONTROL'  THEN 9
        WHEN 'FIRE_SAFETY'        THEN 10
        WHEN 'LABOUR_LICENSE'     THEN 11
        WHEN 'SHOP_INSURANCE'     THEN 12
        WHEN 'DRUG_LICENSE'       THEN 13
        WHEN 'FSSAI_FOOD_LICENSE' THEN 14
        WHEN 'AADHAAR'            THEN 15
        WHEN 'CUSTOM'             THEN 16
        ELSE NULL
    END;

    ALTER TABLE payment_transactions DROP COLUMN document_type;
    ALTER TABLE payment_transactions RENAME COLUMN document_type_ordinal TO document_type;
    ALTER TABLE payment_transactions ALTER COLUMN document_type SET NOT NULL;

  ELSIF col_type = 'integer' THEN
    -- Column was already converted (e.g. manually in dev); nothing to do
    RAISE NOTICE 'payment_transactions.document_type is already INTEGER – skipping conversion.';
  ELSE
    RAISE EXCEPTION 'Unexpected data_type for document_type: %', col_type;
  END IF;
END;
$$;
