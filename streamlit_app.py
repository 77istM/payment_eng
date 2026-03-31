import json
import uuid
from typing import Any

import requests
import streamlit as st

DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_MT103 = (
    ":20:TXREF20231001\n"
    ":23B:CRED\n"
    ":32A:231001USD12500,00\n"
    ":50K:/123456789\n"
    "JOHN DOE\n"
    ":59:/987654321\n"
    "JANE SMITH\n"
    ":71A:SHA\n"
)
DEFAULT_PAIN001 = """<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.03\">
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
          <InstdAmt Ccy=\"EUR\">250.00</InstdAmt>
        </Amt>
        <CdtrAcct>
          <Id><IBAN>DE44500105175407324931</IBAN></Id>
        </CdtrAcct>
      </CdtTrfTxInf>
    </PmtInf>
  </CstmrCdtTrfInitn>
</Document>
"""


def call_api(
    method: str,
    base_url: str,
    path: str,
    headers: dict[str, str] | None = None,
    body: str | None = None,
    timeout_seconds: int = 30,
) -> tuple[int, dict[str, str], str]:
    url = f"{base_url.rstrip('/')}" + path
    response = requests.request(
        method=method,
        url=url,
        headers=headers,
        data=body,
        timeout=timeout_seconds,
    )
    return response.status_code, dict(response.headers), response.text


def render_response(status_code: int, headers: dict[str, str], body: str) -> None:
    if status_code >= 400:
        st.error(f"HTTP {status_code}")
    else:
        st.success(f"HTTP {status_code}")

    st.caption("Response headers")
    st.json(headers)

    content_type = headers.get("Content-Type", "")
    if "application/json" in content_type.lower():
        try:
            st.json(json.loads(body))
        except json.JSONDecodeError:
            st.code(body)
    elif "xml" in content_type.lower():
        st.code(body, language="xml")
    else:
        st.code(body)


st.set_page_config(page_title="Payment Engine Test Console", page_icon="💳", layout="wide")
st.title("Payment Engine Test Console")
st.caption("Streamlit GUI for testing MT103, rail simulation, and ops endpoints")

with st.sidebar:
    st.subheader("Connection")
    base_url = st.text_input("Backend base URL", value=DEFAULT_BASE_URL)
    timeout_seconds = st.number_input("Timeout (seconds)", min_value=1, max_value=120, value=30)

    st.divider()
    st.subheader("Quick health")
    if st.button("Check /actuator/health", use_container_width=True):
        try:
            status, headers, body = call_api("GET", base_url, "/actuator/health", timeout_seconds=int(timeout_seconds))
            render_response(status, headers, body)
        except requests.RequestException as exc:
            st.error(f"Health check failed: {exc}")


mt103_tab, rail_tab, ops_tab = st.tabs(["MT103", "Rail Payments", "Ops"])

with mt103_tab:
    st.subheader("MT103 APIs")
    mt103_message = st.text_area("MT103 raw message", value=DEFAULT_MT103, height=240)

    mt103_col1, mt103_col2, mt103_col3, mt103_col4 = st.columns(4)

    with mt103_col1:
        if st.button("Parse", use_container_width=True):
            try:
                status, headers, body = call_api(
                    "POST",
                    base_url,
                    "/parse",
                    headers={"Content-Type": "text/plain"},
                    body=mt103_message,
                    timeout_seconds=int(timeout_seconds),
                )
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"Parse failed: {exc}")

    with mt103_col2:
        if st.button("Validate", use_container_width=True):
            try:
                status, headers, body = call_api(
                    "POST",
                    base_url,
                    "/validate",
                    headers={"Content-Type": "text/plain"},
                    body=mt103_message,
                    timeout_seconds=int(timeout_seconds),
                )
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"Validate failed: {exc}")

    with mt103_col3:
        if st.button("Convert", use_container_width=True):
            try:
                status, headers, body = call_api(
                    "POST",
                    base_url,
                    "/convert",
                    headers={"Content-Type": "text/plain"},
                    body=mt103_message,
                    timeout_seconds=int(timeout_seconds),
                )
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"Convert failed: {exc}")

    with mt103_col4:
        if st.button("Store", use_container_width=True):
            try:
                status, headers, body = call_api(
                    "POST",
                    base_url,
                    "/store",
                    headers={"Content-Type": "text/plain"},
                    body=mt103_message,
                    timeout_seconds=int(timeout_seconds),
                )
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"Store failed: {exc}")

    st.markdown("### Stored Payments")
    store_col1, store_col2 = st.columns(2)
    with store_col1:
        if st.button("List /store", use_container_width=True):
            try:
                status, headers, body = call_api("GET", base_url, "/store", timeout_seconds=int(timeout_seconds))
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"List store failed: {exc}")
    with store_col2:
        get_id = st.text_input("Get /store/{id}", value="1")
        if st.button("Fetch by ID", use_container_width=True):
            try:
                status, headers, body = call_api("GET", base_url, f"/store/{get_id}", timeout_seconds=int(timeout_seconds))
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"Fetch store by id failed: {exc}")

with rail_tab:
    st.subheader("Rail Payment APIs")

    rail_col1, rail_col2 = st.columns(2)
    with rail_col1:
        selected_rail = st.selectbox("Rail", options=["SEPA", "FPS"], index=0)
    with rail_col2:
        idempotency_key = st.text_input("Idempotency-Key", value=str(uuid.uuid4()))

    pain001_xml = st.text_area("pain.001 XML", value=DEFAULT_PAIN001, height=320)

    if st.button("Initiate Rail Payment", use_container_width=True):
        try:
            status, headers, body = call_api(
                "POST",
                base_url,
                f"/rails/payments?rail={selected_rail}",
                headers={
                    "Content-Type": "application/xml",
                    "Idempotency-Key": idempotency_key,
                },
                body=pain001_xml,
                timeout_seconds=int(timeout_seconds),
            )
            render_response(status, headers, body)
        except requests.RequestException as exc:
            st.error(f"Initiate rail payment failed: {exc}")

    st.markdown("### Settlement and Reconciliation")
    batch_col1, batch_col2 = st.columns(2)
    with batch_col1:
        if st.button("Run /rails/settlements/run", use_container_width=True):
            try:
                status, headers, body = call_api(
                    "POST", base_url, "/rails/settlements/run", timeout_seconds=int(timeout_seconds)
                )
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"Run settlements failed: {exc}")
    with batch_col2:
        if st.button("Run /rails/reconciliation/run", use_container_width=True):
            try:
                status, headers, body = call_api(
                    "POST", base_url, "/rails/reconciliation/run", timeout_seconds=int(timeout_seconds)
                )
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"Run reconciliation failed: {exc}")

    st.markdown("### Rail Queries and Test Controls")
    rails_query_col1, rails_query_col2 = st.columns(2)

    with rails_query_col1:
        if st.button("List /rails/payments", use_container_width=True):
            try:
                status, headers, body = call_api("GET", base_url, "/rails/payments", timeout_seconds=int(timeout_seconds))
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"List rails payments failed: {exc}")

        payment_id_for_query = st.text_input("Payment id", value="1")
        if st.button("Get /rails/payments/{id}", use_container_width=True):
            try:
                status, headers, body = call_api(
                    "GET", base_url, f"/rails/payments/{payment_id_for_query}", timeout_seconds=int(timeout_seconds)
                )
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"Get payment failed: {exc}")

        if st.button("Get /rails/payments/{id}/audit", use_container_width=True):
            try:
                status, headers, body = call_api(
                    "GET",
                    base_url,
                    f"/rails/payments/{payment_id_for_query}/audit",
                    timeout_seconds=int(timeout_seconds),
                )
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"Get audit failed: {exc}")

    with rails_query_col2:
        transient_count = st.number_input("Transient failure count", min_value=0, max_value=20, value=2)
        if st.button("POST /simulate-transient-failures", use_container_width=True):
            try:
                status, headers, body = call_api(
                    "POST",
                    base_url,
                    f"/rails/payments/{payment_id_for_query}/simulate-transient-failures?count={int(transient_count)}",
                    timeout_seconds=int(timeout_seconds),
                )
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"Configure transient failures failed: {exc}")

        debtor_balance = st.text_input("Force low funds debtor balance", value="1.00")
        if st.button("POST /force-failure/low-funds", use_container_width=True):
            try:
                status, headers, body = call_api(
                    "POST",
                    base_url,
                    f"/rails/payments/{payment_id_for_query}/force-failure/low-funds?debtorBalance={debtor_balance}",
                    timeout_seconds=int(timeout_seconds),
                )
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"Force failure failed: {exc}")

with ops_tab:
    st.subheader("Operational Endpoints")

    ops_col1, ops_col2, ops_col3 = st.columns(3)

    with ops_col1:
        if st.button("GET /rails/ops/health", use_container_width=True):
            try:
                status, headers, body = call_api("GET", base_url, "/rails/ops/health", timeout_seconds=int(timeout_seconds))
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"Ops health failed: {exc}")

        if st.button("GET /rails/ops/queue/stats", use_container_width=True):
            try:
                status, headers, body = call_api(
                    "GET", base_url, "/rails/ops/queue/stats", timeout_seconds=int(timeout_seconds)
                )
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"Queue stats failed: {exc}")

    with ops_col2:
        if st.button("GET /rails/ops/queue/dlq", use_container_width=True):
            try:
                status, headers, body = call_api(
                    "GET", base_url, "/rails/ops/queue/dlq", timeout_seconds=int(timeout_seconds)
                )
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"DLQ query failed: {exc}")

    with ops_col3:
        if st.button("POST /rails/ops/drain/start", use_container_width=True):
            try:
                status, headers, body = call_api(
                    "POST", base_url, "/rails/ops/drain/start", timeout_seconds=int(timeout_seconds)
                )
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"Start drain failed: {exc}")

        if st.button("POST /rails/ops/drain/stop", use_container_width=True):
            try:
                status, headers, body = call_api(
                    "POST", base_url, "/rails/ops/drain/stop", timeout_seconds=int(timeout_seconds)
                )
                render_response(status, headers, body)
            except requests.RequestException as exc:
                st.error(f"Stop drain failed: {exc}")

st.divider()
st.caption("Tip: for Streamlit Cloud, set Backend base URL to your reachable backend host (public URL or tunneled endpoint).")
