INSERT INTO merchants (id, tenant_id, name, country, risk_tier) VALUES
('mer_100', 'tenant-pe-01', 'Andes Travel SAC', 'PE', 'STANDARD'),
('mer_101', 'tenant-pe-01', 'Lima Market SAC', 'PE', 'LOW'),
('mer_200', 'tenant-other', 'External Merchant', 'PE', 'STANDARD');

INSERT INTO payments (id, tenant_id, merchant_id, amount, currency, status, created_at, updated_at, processor_reference, decline_code) VALUES
('pay_1001', 'tenant-pe-01', 'mer_101', 159.90, 'PEN', 'CAPTURED',
 '2026-09-18T14:01:00Z', '2026-09-18T14:01:08Z', 'proc_7XA001', NULL),
('pay_1002', 'tenant-pe-01', 'mer_100', 1250.00, 'PEN', 'DECLINED',
 '2026-09-18T14:10:00Z', '2026-09-18T14:10:02Z', 'proc_7XA002', 'DO_NOT_HONOR'),
('pay_1003', 'tenant-pe-01', 'mer_100', 480.50, 'PEN', 'PROCESSING',
 '2026-09-18T14:20:00Z', '2026-09-18T14:20:31Z', 'proc_7XA003', NULL),
('pay_1004', 'tenant-pe-01', 'mer_101', 89.00, 'USD', 'SETTLED',
 '2026-09-17T16:00:00Z', '2026-09-18T22:30:00Z', 'proc_7XA004', NULL),
('pay_2001', 'tenant-other', 'mer_200', 999.00, 'PEN', 'DECLINED',
 '2026-09-18T15:00:00Z', '2026-09-18T15:00:02Z', 'proc_PRIVATE', 'SUSPECTED_FRAUD');

INSERT INTO payment_events (payment_id, sequence_no, event_type, occurred_at, details) VALUES
('pay_1001', 1, 'CREATED', '2026-09-18T14:01:00Z', 'Payment intent created by checkout.'),
('pay_1001', 2, 'AUTHORIZATION_REQUESTED', '2026-09-18T14:01:02Z', 'Authorization submitted to processor.'),
('pay_1001', 3, 'AUTHORIZED', '2026-09-18T14:01:04Z', 'Issuer authorization approved.'),
('pay_1001', 4, 'CAPTURE_REQUESTED', '2026-09-18T14:01:05Z', 'Automatic capture requested.'),
('pay_1001', 5, 'CAPTURED', '2026-09-18T14:01:08Z', 'Capture completed successfully.'),

('pay_1002', 1, 'CREATED', '2026-09-18T14:10:00Z', 'Payment intent created by checkout.'),
('pay_1002', 2, 'AUTHORIZATION_REQUESTED', '2026-09-18T14:10:01Z', 'Authorization submitted to processor.'),
('pay_1002', 3, 'DECLINED', '2026-09-18T14:10:02Z', 'Processor returned decline code DO_NOT_HONOR; no retry was executed.'),

('pay_1003', 1, 'CREATED', '2026-09-18T14:20:00Z', 'Payment intent created by checkout.'),
('pay_1003', 2, 'AUTHORIZATION_REQUESTED', '2026-09-18T14:20:03Z', 'Authorization submitted to processor.'),
('pay_1003', 3, 'AUTHORIZED', '2026-09-18T14:20:05Z', 'Issuer authorization approved.'),
('pay_1003', 4, 'CAPTURE_REQUESTED', '2026-09-18T14:20:10Z', 'Capture submitted to processor.'),
('pay_1003', 5, 'PROCESSOR_TIMEOUT', '2026-09-18T14:20:30Z', 'Processor did not return a definitive capture response before timeout.'),
('pay_1003', 6, 'RETRY_SCHEDULED', '2026-09-18T14:20:31Z', 'Status reconciliation scheduled; payment remains PROCESSING to avoid duplicate capture.'),

('pay_1004', 1, 'CREATED', '2026-09-17T16:00:00Z', 'Payment intent created by checkout.'),
('pay_1004', 2, 'AUTHORIZED', '2026-09-17T16:00:03Z', 'Issuer authorization approved.'),
('pay_1004', 3, 'CAPTURED', '2026-09-17T16:00:06Z', 'Capture completed successfully.'),
('pay_1004', 4, 'SETTLEMENT_SUBMITTED', '2026-09-18T02:00:00Z', 'Payment included in settlement batch.'),
('pay_1004', 5, 'SETTLED', '2026-09-18T22:30:00Z', 'Processor settlement confirmed.'),

('pay_2001', 1, 'CREATED', '2026-09-18T15:00:00Z', 'External tenant payment.'),
('pay_2001', 2, 'DECLINED', '2026-09-18T15:00:02Z', 'External tenant data used only to demonstrate tenant isolation.');
