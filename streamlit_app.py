import json
import uuid
from datetime import datetime, timezone
from base64 import b64encode

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

MAX_HISTORY_ITEMS = 120


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


def ensure_state() -> None:
    if "request_history" not in st.session_state:
        st.session_state.request_history = []
    if "history_counter" not in st.session_state:
        st.session_state.history_counter = 0


def build_auth_headers(
    auth_mode: str,
    bearer_token: str,
    basic_username: str,
    basic_password: str,
    api_key_header: str,
    api_key_value: str,
) -> dict[str, str]:
    if auth_mode == "Bearer" and bearer_token.strip():
        return {"Authorization": f"Bearer {bearer_token.strip()}"}

    if auth_mode == "Basic" and basic_username:
        raw = f"{basic_username}:{basic_password}".encode("utf-8")
        return {"Authorization": f"Basic {b64encode(raw).decode('ascii')}"}

    if auth_mode == "API Key" and api_key_header.strip() and api_key_value.strip():
        return {api_key_header.strip(): api_key_value.strip()}

    return {}


def add_history_entry(
    label: str,
    method: str,
    path: str,
    base_headers: dict[str, str],
    body: str,
    status_code: int | None,
    response_headers: dict[str, str],
    response_body: str,
    error_message: str | None,
) -> None:
    st.session_state.history_counter += 1
    entry = {
        "id": st.session_state.history_counter,
        "timestamp": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
        "label": label,
        "method": method,
        "path": path,
        "base_headers": base_headers,
        "body": body,
        "status_code": status_code,
        "response_headers": response_headers,
        "response_body": response_body,
        "error_message": error_message,
    }
    st.session_state.request_history.insert(0, entry)
    if len(st.session_state.request_history) > MAX_HISTORY_ITEMS:
        st.session_state.request_history = st.session_state.request_history[:MAX_HISTORY_ITEMS]


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


def execute_request(
    label: str,
    method: str,
    base_url: str,
    path: str,
    timeout_seconds: int,
    auth_headers: dict[str, str],
    base_headers: dict[str, str] | None = None,
    body: str | None = None,
    record_history: bool = True,
) -> None:
    body = body or ""
    base_headers = base_headers or {}
    merged_headers = dict(base_headers)
    merged_headers.update(auth_headers)
    try:
        status, headers, response_body = call_api(
            method=method,
            base_url=base_url,
            path=path,
            headers=merged_headers,
            body=body,
            timeout_seconds=timeout_seconds,
        )
        render_response(status, headers, response_body)
        if record_history:
            add_history_entry(
                label=label,
                method=method,
                path=path,
                base_headers=base_headers,
                body=body,
                status_code=status,
                response_headers=headers,
                response_body=response_body,
                error_message=None,
            )
    except requests.RequestException as exc:
        message = str(exc)
        st.error(f"{label} failed: {message}")
        if record_history:
            add_history_entry(
                label=label,
                method=method,
                path=path,
                base_headers=base_headers,
                body=body,
                status_code=None,
                response_headers={},
                response_body="",
                error_message=message,
            )


st.set_page_config(page_title="Payment Engine Test Console", page_icon="💳", layout="wide")
ensure_state()
st.title("Payment Engine Test Console")
st.caption("Streamlit GUI for testing MT103, rail simulation, and ops endpoints")

with st.sidebar:
    st.subheader("Connection")
    base_url = st.text_input("Backend base URL", value=DEFAULT_BASE_URL)
    timeout_seconds = st.number_input("Timeout (seconds)", min_value=1, max_value=120, value=30)

    st.divider()
    st.subheader("Authentication")
    auth_mode = st.selectbox("Auth mode", options=["None", "Bearer", "Basic", "API Key"], index=0)

    bearer_token = ""
    basic_username = ""
    basic_password = ""
    api_key_header = "X-API-Key"
    api_key_value = ""

    if auth_mode == "Bearer":
        bearer_token = st.text_input("Bearer token", type="password")
    elif auth_mode == "Basic":
        basic_username = st.text_input("Username")
        basic_password = st.text_input("Password", type="password")
    elif auth_mode == "API Key":
        api_key_header = st.text_input("Header name", value="X-API-Key")
        api_key_value = st.text_input("Header value", type="password")

    auth_headers = build_auth_headers(
        auth_mode=auth_mode,
        bearer_token=bearer_token,
        basic_username=basic_username,
        basic_password=basic_password,
        api_key_header=api_key_header,
        api_key_value=api_key_value,
    )

    if auth_headers:
        st.caption("Auth headers active for all requests")
    else:
        st.caption("No auth headers configured")

    st.divider()
    st.subheader("Quick health")
    if st.button("Check /actuator/health", use_container_width=True):
        execute_request(
            label="Health check",
            method="GET",
            base_url=base_url,
            path="/actuator/health",
            timeout_seconds=int(timeout_seconds),
            auth_headers=auth_headers,
        )


mt103_tab, rail_tab, ops_tab, history_tab = st.tabs(["MT103", "Rail Payments", "Ops", "History"])

with mt103_tab:
    st.subheader("MT103 APIs")
    mt103_message = st.text_area("MT103 raw message", value=DEFAULT_MT103, height=240)

    mt103_col1, mt103_col2, mt103_col3, mt103_col4 = st.columns(4)

    with mt103_col1:
        if st.button("Parse", use_container_width=True):
            execute_request(
                label="Parse",
                method="POST",
                base_url=base_url,
                path="/parse",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
                base_headers={"Content-Type": "text/plain"},
                body=mt103_message,
            )

    with mt103_col2:
        if st.button("Validate", use_container_width=True):
            execute_request(
                label="Validate",
                method="POST",
                base_url=base_url,
                path="/validate",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
                base_headers={"Content-Type": "text/plain"},
                body=mt103_message,
            )

    with mt103_col3:
        if st.button("Convert", use_container_width=True):
            execute_request(
                label="Convert",
                method="POST",
                base_url=base_url,
                path="/convert",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
                base_headers={"Content-Type": "text/plain"},
                body=mt103_message,
            )

    with mt103_col4:
        if st.button("Store", use_container_width=True):
            execute_request(
                label="Store",
                method="POST",
                base_url=base_url,
                path="/store",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
                base_headers={"Content-Type": "text/plain"},
                body=mt103_message,
            )

    st.markdown("### Stored Payments")
    store_col1, store_col2 = st.columns(2)
    with store_col1:
        if st.button("List /store", use_container_width=True):
            execute_request(
                label="List /store",
                method="GET",
                base_url=base_url,
                path="/store",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
            )
    with store_col2:
        get_id = st.text_input("Get /store/{id}", value="1")
        if st.button("Fetch by ID", use_container_width=True):
            execute_request(
                label="Fetch store by id",
                method="GET",
                base_url=base_url,
                path=f"/store/{get_id}",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
            )

with rail_tab:
    st.subheader("Rail Payment APIs")

    rail_col1, rail_col2 = st.columns(2)
    with rail_col1:
        selected_rail = st.selectbox("Rail", options=["SEPA", "FPS"], index=0)
    with rail_col2:
        idempotency_key = st.text_input("Idempotency-Key", value=str(uuid.uuid4()))

    pain001_xml = st.text_area("pain.001 XML", value=DEFAULT_PAIN001, height=320)

    if st.button("Initiate Rail Payment", use_container_width=True):
        execute_request(
            label="Initiate rail payment",
            method="POST",
            base_url=base_url,
            path=f"/rails/payments?rail={selected_rail}",
            timeout_seconds=int(timeout_seconds),
            auth_headers=auth_headers,
            base_headers={
                "Content-Type": "application/xml",
                "Idempotency-Key": idempotency_key,
            },
            body=pain001_xml,
        )

    st.markdown("### Settlement and Reconciliation")
    batch_col1, batch_col2 = st.columns(2)
    with batch_col1:
        if st.button("Run /rails/settlements/run", use_container_width=True):
            execute_request(
                label="Run settlements",
                method="POST",
                base_url=base_url,
                path="/rails/settlements/run",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
            )
    with batch_col2:
        if st.button("Run /rails/reconciliation/run", use_container_width=True):
            execute_request(
                label="Run reconciliation",
                method="POST",
                base_url=base_url,
                path="/rails/reconciliation/run",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
            )

    st.markdown("### Rail Queries and Test Controls")
    rails_query_col1, rails_query_col2 = st.columns(2)

    with rails_query_col1:
        if st.button("List /rails/payments", use_container_width=True):
            execute_request(
                label="List rails payments",
                method="GET",
                base_url=base_url,
                path="/rails/payments",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
            )

        payment_id_for_query = st.text_input("Payment id", value="1")
        if st.button("Get /rails/payments/{id}", use_container_width=True):
            execute_request(
                label="Get rail payment",
                method="GET",
                base_url=base_url,
                path=f"/rails/payments/{payment_id_for_query}",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
            )

        if st.button("Get /rails/payments/{id}/audit", use_container_width=True):
            execute_request(
                label="Get rail audit",
                method="GET",
                base_url=base_url,
                path=f"/rails/payments/{payment_id_for_query}/audit",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
            )

    with rails_query_col2:
        transient_count = st.number_input("Transient failure count", min_value=0, max_value=20, value=2)
        if st.button("POST /simulate-transient-failures", use_container_width=True):
            execute_request(
                label="Configure transient failures",
                method="POST",
                base_url=base_url,
                path=f"/rails/payments/{payment_id_for_query}/simulate-transient-failures?count={int(transient_count)}",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
            )

        debtor_balance = st.text_input("Force low funds debtor balance", value="1.00")
        if st.button("POST /force-failure/low-funds", use_container_width=True):
            execute_request(
                label="Force low funds failure",
                method="POST",
                base_url=base_url,
                path=f"/rails/payments/{payment_id_for_query}/force-failure/low-funds?debtorBalance={debtor_balance}",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
            )

with ops_tab:
    st.subheader("Operational Endpoints")

    ops_col1, ops_col2, ops_col3 = st.columns(3)

    with ops_col1:
        if st.button("GET /rails/ops/health", use_container_width=True):
            execute_request(
                label="Ops health",
                method="GET",
                base_url=base_url,
                path="/rails/ops/health",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
            )

        if st.button("GET /rails/ops/queue/stats", use_container_width=True):
            execute_request(
                label="Queue stats",
                method="GET",
                base_url=base_url,
                path="/rails/ops/queue/stats",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
            )

    with ops_col2:
        if st.button("GET /rails/ops/queue/dlq", use_container_width=True):
            execute_request(
                label="DLQ query",
                method="GET",
                base_url=base_url,
                path="/rails/ops/queue/dlq",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
            )

    with ops_col3:
        if st.button("POST /rails/ops/drain/start", use_container_width=True):
            execute_request(
                label="Start drain",
                method="POST",
                base_url=base_url,
                path="/rails/ops/drain/start",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
            )

        if st.button("POST /rails/ops/drain/stop", use_container_width=True):
            execute_request(
                label="Stop drain",
                method="POST",
                base_url=base_url,
                path="/rails/ops/drain/stop",
                timeout_seconds=int(timeout_seconds),
                auth_headers=auth_headers,
            )

with history_tab:
    st.subheader("Request History")
    st.caption("Every request from this session is stored here. Use replay to run the same request again.")

    history_actions_col1, history_actions_col2 = st.columns(2)
    with history_actions_col1:
        st.metric("Stored entries", value=len(st.session_state.request_history))
    with history_actions_col2:
        if st.button("Clear history", type="secondary", use_container_width=True):
            st.session_state.request_history = []
            st.success("Request history cleared")

    if not st.session_state.request_history:
        st.info("No requests yet. Trigger an endpoint call from any tab to populate history.")
    else:
        for entry in st.session_state.request_history:
            status_text = (
                f"HTTP {entry['status_code']}" if entry["status_code"] is not None else f"ERROR: {entry['error_message']}"
            )
            title = f"{entry['timestamp']} | {entry['method']} {entry['path']} | {status_text}"
            with st.expander(title):
                st.write(f"Action: {entry['label']}")
                st.caption("Request headers (without auth headers)")
                st.json(entry["base_headers"])
                if entry["body"]:
                    st.caption("Request body")
                    st.code(entry["body"])

                replay_col1, replay_col2 = st.columns(2)
                with replay_col1:
                    if st.button("Replay", key=f"replay_{entry['id']}", use_container_width=True):
                        execute_request(
                            label=f"Replay: {entry['label']}",
                            method=entry["method"],
                            base_url=base_url,
                            path=entry["path"],
                            timeout_seconds=int(timeout_seconds),
                            auth_headers=auth_headers,
                            base_headers=entry["base_headers"],
                            body=entry["body"],
                            record_history=True,
                        )

                with replay_col2:
                    if entry["status_code"] is not None:
                        st.caption("Last response")
                        st.write(f"HTTP {entry['status_code']}")
                    else:
                        st.caption("Last error")
                        st.write(entry["error_message"])

st.divider()
st.caption("Tip: for Streamlit Cloud, set Backend base URL to your reachable backend host (public URL or tunneled endpoint).")
