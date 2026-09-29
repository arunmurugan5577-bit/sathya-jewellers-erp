-- ===========================================================================
-- V6: configurable, gap-free document numbering.
--
-- Why not a PostgreSQL SEQUENCE: a sequence value handed to a transaction that
-- later rolls back is lost for ever, which leaves gaps. Indian GST rules require
-- tax invoices to carry a consecutive serial number, so a failed sale must not
-- burn one. A counter row locked FOR UPDATE inside the same transaction as the
-- document rolls back with it.
--
-- Format, assembled by DocumentNumberService:
--     [prefix][sep][period][sep][zero-padded sequence]
--     INV-2026-000001   (financial year 2026-27, which starts 1 April 2026)
--
-- max_length defaults to 16 for tax documents: the GST invoice rules cap the
-- invoice serial number at sixteen characters.
-- ===========================================================================

CREATE TABLE document_series (
    code           VARCHAR(30)  PRIMARY KEY,
    description    VARCHAR(150) NOT NULL,
    prefix         VARCHAR(10)  NOT NULL DEFAULT '',
    separator_char VARCHAR(1)   NOT NULL DEFAULT '-',
    period_format  VARCHAR(20)  NOT NULL,
    pad_width      SMALLINT     NOT NULL,
    max_length     SMALLINT     NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by     VARCHAR(100),
    CONSTRAINT ck_document_series_prefix    CHECK (prefix ~ '^[A-Z0-9]*$'),
    CONSTRAINT ck_document_series_separator CHECK (separator_char IN ('', '-', '/')),
    CONSTRAINT ck_document_series_period    CHECK (period_format IN ('NONE', 'CALENDAR_YEAR', 'FINANCIAL_YEAR')),
    CONSTRAINT ck_document_series_padding   CHECK (pad_width BETWEEN 1 AND 12),
    CONSTRAINT ck_document_series_length    CHECK (max_length BETWEEN 4 AND 30)
);

COMMENT ON TABLE document_series IS 'Numbering rules per document type. Edit a row to change a format; history is unaffected.';

CREATE TABLE document_counters (
    series_code VARCHAR(30) NOT NULL,
    period_key  VARCHAR(10) NOT NULL,
    next_value  BIGINT      NOT NULL DEFAULT 1,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_document_counters        PRIMARY KEY (series_code, period_key),
    CONSTRAINT fk_document_counters_series FOREIGN KEY (series_code) REFERENCES document_series (code) ON DELETE RESTRICT,
    CONSTRAINT ck_document_counters_next   CHECK (next_value > 0)
);

COMMENT ON TABLE document_counters IS 'Next number per series and period. period_key is ALL for series that never reset.';

INSERT INTO document_series (code, description, prefix, separator_char, period_format, pad_width, max_length, updated_by) VALUES
    ('SALE_INVOICE',       'Sales tax invoice',               'INV', '-', 'FINANCIAL_YEAR', 6, 16, 'system'),
    ('OLD_METAL_PURCHASE', 'Old gold / silver purchase bill', 'PB',  '-', 'FINANCIAL_YEAR', 6, 16, 'system'),
    ('CUSTOMER',           'Customer code',                   'CUS', '-', 'NONE',           6, 20, 'system');
