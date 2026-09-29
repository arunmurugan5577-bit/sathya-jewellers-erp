-- ===========================================================================
-- V20: printing from a server that is not in the shop.
--
-- Direct printing works by asking the operating system the application runs on
-- for its printers. That is fine while the application runs on the counter PC.
-- Hosted, it asks a machine in a data centre, which has no printers and no way
-- to reach the ones on the shop's USB cable.
--
-- So the server stops printing and starts queueing. It renders each page to a
-- bitmap at the printer's own resolution - the same drawing code, so the tag
-- comes out identical - and a small agent on the shop PC collects the pages and
-- spools them to the real printer.
--
--   print_mode = DIRECT   the printer is on this machine (unchanged behaviour)
--   print_mode = AGENT    queue the pages for the shop's print agent
--
-- DIRECT stays the default: an existing install must not change how it prints
-- because of a migration.
-- ===========================================================================

ALTER TABLE label_settings
    ADD COLUMN print_mode VARCHAR(10) NOT NULL DEFAULT 'DIRECT';

ALTER TABLE label_settings
    ADD CONSTRAINT ck_label_settings_print_mode CHECK (print_mode IN ('DIRECT', 'AGENT'));

COMMENT ON COLUMN label_settings.print_mode IS
    'DIRECT prints from this machine; AGENT queues pages for the print agent in the shop.';

-- --- the queue ------------------------------------------------------------
-- One row per printed page. Pages rather than jobs so that a run which fails
-- half way can be retried from where it stopped, and so the agent never has to
-- hold a whole batch in memory.
CREATE TABLE label_print_queue (
    id              BIGSERIAL     PRIMARY KEY,
    job_id          BIGINT        NOT NULL REFERENCES label_print_jobs (id),
    page_no         INTEGER       NOT NULL,
    image           BYTEA         NOT NULL,
    width_mm        NUMERIC(6,2)  NOT NULL,
    height_mm       NUMERIC(6,2)  NOT NULL,
    printer_name    VARCHAR(160),
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER       NOT NULL DEFAULT 0,
    claimed_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    error_message   VARCHAR(500),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_label_queue_status CHECK (status IN ('PENDING', 'CLAIMED', 'DONE', 'FAILED')),
    CONSTRAINT ck_label_queue_page   CHECK (page_no >= 1),
    CONSTRAINT uq_label_queue_page   UNIQUE (job_id, page_no)
);

-- The agent asks for the oldest pending pages, over and over. This is the only
-- query that matters for its latency.
CREATE INDEX ix_label_queue_pending ON label_print_queue (status, id)
    WHERE status IN ('PENDING', 'CLAIMED');

COMMENT ON TABLE label_print_queue IS
    'Pages waiting for the shop print agent. One row per printed page, in order.';
COMMENT ON COLUMN label_print_queue.image IS
    'The page as a 1-bit PNG at the printer resolution, rendered by the server.';

-- --- what the agent tells us about itself ---------------------------------
CREATE TABLE label_print_agent (
    id              SMALLINT      PRIMARY KEY,
    last_seen_at    TIMESTAMPTZ,
    agent_version   VARCHAR(40),
    host_name       VARCHAR(160),
    printers        TEXT,
    default_printer VARCHAR(160),
    CONSTRAINT ck_label_agent_singleton CHECK (id = 1)
);

INSERT INTO label_print_agent (id) VALUES (1);

COMMENT ON TABLE label_print_agent IS
    'Last contact from the shop print agent, and the printers it can see there.';
COMMENT ON COLUMN label_print_agent.printers IS
    'Newline separated printer names, as reported by the shop PC.';

-- --- the agent's account ------------------------------------------------
-- The agent signs in as an ordinary user and reuses LABEL_CREATE. A separate
-- authority would need a new action, and the permissions table allows only
-- CREATE, VIEW, EDIT and DELETE - not worth reshaping the model for one
-- machine account whose password lives on the shop PC and nowhere else.
