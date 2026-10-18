CREATE TABLE merchants (
    id          VARCHAR(64) PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL,
    name        VARCHAR(160) NOT NULL,
    country     CHAR(2) NOT NULL,
    risk_tier   VARCHAR(20) NOT NULL,
    CONSTRAINT uq_merchant_tenant UNIQUE (tenant_id, id)
);

CREATE TABLE payments (
    id                    VARCHAR(64) PRIMARY KEY,
    tenant_id             VARCHAR(64) NOT NULL,
    merchant_id           VARCHAR(64) NOT NULL,
    amount                NUMERIC(18,2) NOT NULL CHECK (amount >= 0),
    currency              CHAR(3) NOT NULL,
    status                VARCHAR(24) NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    processor_reference   VARCHAR(120),
    decline_code          VARCHAR(64),
    CONSTRAINT fk_payment_merchant FOREIGN KEY (merchant_id) REFERENCES merchants(id),
    CONSTRAINT uq_payment_tenant UNIQUE (tenant_id, id)
);

CREATE INDEX idx_payments_tenant_merchant ON payments(tenant_id, merchant_id);
CREATE INDEX idx_payments_tenant_status ON payments(tenant_id, status);

CREATE TABLE payment_events (
    payment_id    VARCHAR(64) NOT NULL REFERENCES payments(id),
    sequence_no   BIGINT NOT NULL,
    event_type    VARCHAR(40) NOT NULL,
    occurred_at   TIMESTAMPTZ NOT NULL,
    details       TEXT NOT NULL,
    PRIMARY KEY (payment_id, sequence_no)
);

CREATE INDEX idx_payment_events_payment_time ON payment_events(payment_id, occurred_at);


CREATE TABLE copilot_conversations(tenant_id VARCHAR(64) NOT NULL,id VARCHAR(64) NOT NULL,payment_id VARCHAR(64) NOT NULL,created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(tenant_id,id));
CREATE TABLE copilot_runs(id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(64) NOT NULL,conversation_id VARCHAR(64) NOT NULL,request_id VARCHAR(64) NOT NULL,payment_id VARCHAR(64) NOT NULL,question TEXT NOT NULL,review_requested BOOLEAN NOT NULL,status VARCHAR(32) NOT NULL,report TEXT,narrative TEXT,created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,UNIQUE(tenant_id,conversation_id,request_id));
CREATE TABLE copilot_messages(id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,tenant_id VARCHAR(64) NOT NULL,conversation_id VARCHAR(64) NOT NULL,run_id VARCHAR(64) NOT NULL,role VARCHAR(16) NOT NULL,content TEXT NOT NULL,created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,UNIQUE(run_id,role));
CREATE INDEX idx_copilot_memory ON copilot_messages(tenant_id,conversation_id,id);
CREATE TABLE copilot_bootstrap(fingerprint VARCHAR(128) PRIMARY KEY,chunks INTEGER NOT NULL,created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP);
