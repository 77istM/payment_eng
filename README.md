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

## Streamlit Test GUI (Cloud Ready)

A Streamlit test console is included to exercise MT103, rail simulation, and ops endpoints from a browser.

Files:
- `streamlit_app.py`
- `requirements.txt`

### Run locally

1. Install Python dependencies:

```bash
pip install -r requirements.txt
```

2. Start the Spring Boot backend (default `http://localhost:8080`).

3. Start the GUI:

```bash
streamlit run streamlit_app.py
```

### Deploy on Streamlit Cloud

1. Push this repository branch to GitHub.
2. In Streamlit Cloud, create a new app from the repository.
3. Set app file path to `streamlit_app.py`.
4. Keep `requirements.txt` as dependency source.
5. After deploy, set **Backend base URL** in the app sidebar to your reachable backend URL.

Notes:
- The GUI sends raw requests to your backend APIs and displays HTTP status, headers, and response payloads.
- If backend is not publicly reachable, expose it through your preferred tunnel/reverse proxy first.
- The sidebar supports simple auth modes for secured environments: `Bearer`, `Basic`, and custom `API Key` header.
- The `History` tab stores recent requests and supports one-click replay for repeatable test runs.

## Java and Maven Troubleshooting

This project targets Java 17. If you see an error like:

`Apache Maven 4.x requires Java 17 or newer to run`

use the repo helper script that auto-detects a Java 17+ installation and runs Maven with it:

```bash
bash scripts/mvn-java17.sh test
```

or from repository root:

```bash
bash mvn-java17.sh test
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

If `apt-get update` fails with a Yarn GPG key error (`NO_PUBKEY 62D54FD4003F6525`), temporarily disable Yarn repo and install Java 17:

```bash
sudo mv /etc/apt/sources.list.d/yarn.list /etc/apt/sources.list.d/yarn.list.disabled 2>/dev/null || true
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk
```

## Quick Start: Testing in Codespaces Terminal

Copy and paste these blocks directly into your Codespaces terminal.

### Block A: Setup + run all tests

```bash
set -euo pipefail

cd /workspaces/payment_eng
chmod +x scripts/mvn-java17.sh

# Verify Java (project requires Java 17+)
java -version

# If Java 17+ is NOT installed, uncomment and run the next 4 lines:
# sudo apt-get update
# sudo apt-get install -y openjdk-17-jdk
# export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
# export PATH="$JAVA_HOME/bin:$PATH"

# Run full unit/integration test suite
bash scripts/mvn-java17.sh test
```

### Block B: Fast iteration commands

```bash
cd /workspaces/payment_eng

# Run one test class
bash scripts/mvn-java17.sh -Dtest=MT103ValidatorTest test

# Run integration tests only
bash scripts/mvn-java17.sh -Dtest='*IT' test
```

### Block C: API smoke test (start app, test endpoints, stop app)

```bash
set -euo pipefail

cd /workspaces/payment_eng

# Start app in background
bash scripts/mvn-java17.sh -q spring-boot:run >/tmp/payment-eng.log 2>&1 &
echo $! >/tmp/payment-eng.pid

# Wait for app to become healthy (max ~60s)
for i in {1..30}; do
	if curl -fsS http://localhost:8080/actuator/health >/dev/null; then
		break
	fi
	sleep 2
done

# 1) Health check
curl -fsS http://localhost:8080/actuator/health
echo

# 2) MT103 parse check
curl -fsS -X POST http://localhost:8080/parse \
	-H 'Content-Type: text/plain' \
	--data-binary $':20:TXREF20231001\n:23B:CRED\n:32A:231001USD12500,00\n:50K:/123456789\nJOHN DOE\n:59:/987654321\nJANE SMITH\n:71A:SHA\n'
echo

# 3) Rail simulator initiate payment (pain.001)
curl -fsS -X POST 'http://localhost:8080/rails/payments?rail=SEPA' \
	-H 'Content-Type: application/xml' \
	-H "Idempotency-Key: $(cat /proc/sys/kernel/random/uuid)" \
	--data-binary '<?xml version="1.0" encoding="UTF-8"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.03">
	<CstmrCdtTrfInitn>
		<GrpHdr><MsgId>MSG-1001</MsgId></GrpHdr>
		<PmtInf>
			<PmtInfId>PMTINF-1</PmtInfId>
			<DbtrAcct><Id><IBAN>DE89370400440532013000</IBAN></Id></DbtrAcct>
			<CdtTrfTxInf>
				<PmtId><InstrId>INST-1001</InstrId><EndToEndId>E2E-1001</EndToEndId></PmtId>
				<Amt><InstdAmt Ccy="EUR">250.00</InstdAmt></Amt>
				<CdtrAcct><Id><IBAN>DE44500105175407324931</IBAN></Id></CdtrAcct>
			</CdtTrfTxInf>
		</PmtInf>
	</CstmrCdtTrfInitn>
</Document>'
echo

# Optional: inspect logs if needed
tail -n 80 /tmp/payment-eng.log || true

# Stop app
if [[ -f /tmp/payment-eng.pid ]]; then
	kill "$(cat /tmp/payment-eng.pid)" || true
fi
rm -f /tmp/payment-eng.pid
```

If startup fails, inspect logs:

```bash
tail -n 120 /tmp/payment-eng.log
```