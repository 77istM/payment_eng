# payment_eng

Spring Boot payment engineering sandbox with two flows:

1. SWIFT MT103 parse/validate/convert/store APIs.
2. SEPA + Faster Payments rail simulator with lifecycle, settlement, reconciliation, idempotency, and audit trail.

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

### Run Settlement Batch

`POST /rails/settlements/run`

Processes all `PENDING` payments and updates ledger balances.

### Run Reconciliation

`POST /rails/reconciliation/run`

Compares expected balances to actual balances per account.

### Payment and Audit Queries

- `GET /rails/payments`
- `GET /rails/payments/{id}`
- `GET /rails/payments/{id}/audit`

## Existing MT103 APIs

- `POST /parse`
- `POST /validate`
- `POST /convert`
- `POST /store`
- `GET /store`
- `GET /store/{id}`