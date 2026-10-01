-- GST registration certificates are permanent: they carry no expiry date and
-- must never be treated as expiring documents.
--
-- The self-upload path used to run every uploaded document through expiry-date
-- OCR, so for GST it scraped a meaningless date out of the certificate's date
-- fields (both affected rows got 2020-03-07) and the expiry scheduler then
-- flagged the shops as EXPIRED and sent alerts. Upload no longer extracts an
-- expiry for GST and the scheduler skips GST outright, so this clears the rows
-- the old behaviour damaged.
--
-- issue_date is left alone: a GST registration genuinely has an issue date.

UPDATE documents
SET expiry_date = NULL,
    status = CASE WHEN status = 'EXPIRED' THEN 'UPLOADED' ELSE status END
WHERE document_type = 'GST'
  AND (expiry_date IS NOT NULL OR status = 'EXPIRED');
