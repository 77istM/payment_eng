# payment_eng

Spring Boot payment engineering sandbox with two flows:

1. SWIFT MT103 parse/validate/convert/store APIs.
2. High-availability rail payment processor simulator with asynchronous settlement, retries, DLQ, idempotency, and reconciliation.

## High-Availability / Low-Latency Features

- Stateless API layer suitable for horizontal scaling.
- Health endpoints with liveness/readiness probes through actuator.
- Idempotent initiation (`Idempotency-Key`) with unique database constraint and replay-safe behavior.
- ACID settlement operations using transactional boundaries plus pessimistic locking and optimistic versioning.
- Kafka-like in-memory queue simulation for settlement events.
- Exponential backoff retry and dead-letter queue (DLQ) for exhausted retries.
- Graceful shutdown support and drain-mode endpoints to simulate zero-downtime deployment.

## Rail Simulator APIs

### Initiate Payment (pain.001)

`POST /rails/payments?rail=SEPA|FPS`

Headers:
- `Content-Type: application/xml`
- `Idempotency-Key: <unique-key>`

Body (example):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.03">
	<CstmrCdtTrfInitn>
		<GrpHdr>
			<MsgId>MSG-1001</MsgId>
		</GrpHdr>
		<PmtInf>
			<PmtInfId>PMTINF-1</PmtInfId>
			<DbtrAcct>
				<Id><IBAN>DE89370400440532013000</IBAN></Id>
			</DbtrAcct>
			<CdtTrfTxInf>
				<PmtId>
					<InstrId>INST-1001</InstrId>
					<EndToEndId>E2E-1001</EndToEndId>
				</PmtId>
				<Amt>
					<InstdAmt Ccy="EUR">250.00</InstdAmt>
				</Amt>
				<CdtrAcct>
					<Id><IBAN>DE44500105175407324931</IBAN></Id>
				</CdtrAcct>
			</CdtTrfTxInf>
		</PmtInf>
	</CstmrCdtTrfInitn>
</Document>
```

Lifecycle:
- `INITIATED -> PENDING -> SETTLED -> COMPLETED`
- Fail path: `PENDING -> FAILED`
- Initiation is asynchronous: endpoint returns accepted response and settlement is consumed from the in-memory queue.

### Run Settlement Batch

`POST /rails/settlements/run`

Processes all `PENDING` payments and updates ledger balances.

### Force Failed Branch (Low Funds)

`POST /rails/payments/{id}/force-failure/low-funds?debtorBalance=1.00`

For test scenarios, this endpoint sets the debtor ledger balance below the payment amount and runs settlement immediately so the payment moves to `FAILED` with an insufficient funds reason.

### Run Reconciliation

`POST /rails/reconciliation/run`

Compares expected balances to actual balances per account.

### Payment and Audit Queries

- `GET /rails/payments`
- `GET /rails/payments/{id}`
- `GET /rails/payments/{id}/audit`

## Operational APIs

- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`
- `GET /rails/ops/health`
- `GET /rails/ops/queue/stats`
- `GET /rails/ops/queue/dlq`
- `POST /rails/ops/drain/start`
- `POST /rails/ops/drain/stop`

### Retry/DLQ Failure Injection

For retry-path testing, configure transient failures before settlement succeeds:

`POST /rails/payments/{id}/simulate-transient-failures?count=3`

This causes the next `count` settlement attempts to fail transiently, triggering exponential backoff retries and eventually DLQ if retries are exhausted.

## Load Testing (JMeter)

Artifacts:

- `load-test/jmeter/payment-load-test.jmx`
- `load-test/jmeter/pain001-template.xml`

Example non-GUI run:

```bash
jmeter -n \
	-t load-test/jmeter/payment-load-test.jmx \
	-l load-test/jmeter/results.jtl \
	-Jhost=localhost \
	-Jport=8080 \
	-Jthreads=150 \
	-Jrampup=30 \
	-Jloops=200
```

Suggested success criteria:

- P95 latency under target SLA.
- No failed requests due to duplicate idempotency keys (keys are UUID-generated).
- Queue drains to near-zero depth after traffic subsides.
- DLQ remains empty during normal conditions.

## Existing MT103 APIs

- `POST /parse`
- `POST /validate`
- `POST /convert`
- `POST /store`
- `GET /store`
- `GET /store/{id}`

## Java and Maven Troubleshooting

This project targets Java 17. If you see an error like:

`Apache Maven 4.x requires Java 17 or newer to run`

use the repo helper script that auto-detects a Java 17+ installation and runs Maven with it:

```bash
bash scripts/mvn-java17.sh test
```

For full build including integration tests and reports:

```bash
bash scripts/mvn-java17.sh verify
```

If no Java 17 installation is found, inspect candidates with:

```bash
update-alternatives --list java
ls -d /usr/lib/jvm/*
```