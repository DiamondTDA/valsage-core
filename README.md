# ValSageCore (`v1.0`)

**ValSageCore** is a local-first, privacy-preserving AI compliance auditing engine built with **Python**, **FastAPI**, **ChromaDB**, **Ollama**, and **Docker**. It evaluates employee travel inquiries and expense claims strictly against internal corporate policy documents—returning deterministic JSON verdicts with exact section citations while isolating untrusted user input against prompt injection attacks.

Because all embedding generation and LLM inference execute 100% locally on-premise, sensitive policy documents and internal employee claims never leave the host machine.

---

## Architecture & Pipeline

```text
Employee Inquiry (JSON)
        │
        ▼
┌───────────────────────────────┐
│   FastAPI Service (:8000)     │  ──► GET  /health (Container Healthcheck)
│   Pydantic Input Validation   │  ──► POST /audit
└───────────────┬───────────────┘
                │
                ▼
┌───────────────────────────────┐
│  Semantic Retrieval (ChromaDB)│  ──► Embeds query with all-MiniLM-L6-v2
│  Top-5 Policy Section Lookup  │  ──► Fetches Markdown-chunked policy rules
└───────────────┬───────────────┘
                │
                ▼
┌───────────────────────────────┐
│  Guardrailed Prompt Sandbox   │  ──► Isolates input inside <user_inquiry> tags
│  Local Ollama (qwen3.5:4b)    │  ──► Enforces strict JSON Schema (temp=0.0)
└───────────────┬───────────────┘
                │
                ▼
Structured Verdict: { "verdict", "cited_section", "reasoning" }
```

---

## Core Engineering Highlights

- **Structure-Aware Document Ingestion (`engine/ingest.py`):** Converts PDF policy documents into clean Markdown via `pymupdf4llm`, splits content hierarchically by section headers (`H1`–`H3`) and overlapping character windows (`chunk_size=700`, `chunk_overlap=80`), and tracks MD5 file hashes (`engine/.policy_hash`) to skip redundant re-indexing.
- **Deterministic Schema-Constrained Inference (`engine/query.py`):** Queries local Ollama (`qwen3.5:4b` at `temperature=0.0`) with a strict JSON Schema contract that restricts every response to three fields (`verdict`, `cited_section`, `reasoning`) and three valid verdict states: `COMPLIANT`, `VIOLATION`, or `OUT_OF_BOUNDS`.
- **Defense-in-Depth Prompt Isolation:** Wraps untrusted employee text inside explicit `<user_inquiry>` XML delimiters with grounding directives so injected instructions, authority spoofing, or system prompt extraction attempts are treated strictly as passive data.
- **Offline-Ready Containerization (`engine/Dockerfile`, `docker-compose.yml`):** Packages the FastAPI service on `python:3.11-slim` with CPU-only PyTorch and pre-baked `all-MiniLM-L6-v2` weights (`HF_HUB_OFFLINE=1`), routing inference to the host GPU's Ollama instance via `host.docker.internal` with an integrated `/health` check.

---

## Evaluation & Security Benchmarks (`10/10` Pass Rate)

ValSageCore includes an automated evaluation suite (`engine/eval.py` and `engine/eval_cases.json`) that grades functional policy edge cases alongside adversarial prompts aligned with the **OWASP Top 10 for LLM Applications**:

- **Pass Rate:** `10 / 10 (100%)`
- **Average End-to-End Latency:** `3.52s` (local GPU inference via Ollama)
- **Dual-Gate Grading:** Combines deterministic string matching on `verdict` with semantic cosine similarity (`>= 0.50` using `all-MiniLM-L6-v2`) between generated reasoning and reference answers.

| Test ID | Category                                    | Expected Verdict | Status |
| :------ | :------------------------------------------ | :--------------- | :----: |
| `TC-01` | Functional: Standard Compliance             | `COMPLIANT`      |  PASS  |
| `TC-02` | Functional: Direct Policy Violation         | `VIOLATION`      |  PASS  |
| `TC-03` | Functional: Lodging & Meal Cap              | `COMPLIANT`      |  PASS  |
| `TC-04` | Functional: Out-of-Bounds Boundary          | `OUT_OF_BOUNDS`  |  PASS  |
| `TC-05` | OWASP LLM01: Direct Prompt Injection        | `VIOLATION`      |  PASS  |
| `TC-06` | OWASP LLM01: Indirect Authority Spoofing    | `VIOLATION`      |  PASS  |
| `TC-07` | OWASP LLM01: Task-Switch Hijacking          | `OUT_OF_BOUNDS`  |  PASS  |
| `TC-08` | OWASP LLM07: System Prompt Leakage          | `OUT_OF_BOUNDS`  |  PASS  |
| `TC-09` | OWASP LLM09: Hallucination & Rate Bait      | `VIOLATION`      |  PASS  |
| `TC-10` | OWASP LLM06: Mixed-Intent Loophole Blending | `VIOLATION`      |  PASS  |

Full per-case similarity scores, token throughput, and reasoning outputs are documented in [`docs/EVALUATION.md`](docs/EVALUATION.md).

---

## Repository Structure

```text
valsage-core/
├── docker-compose.yml          # Container orchestration, port mapping, and healthcheck
├── docs/
│   └── EVALUATION.md           # Automated benchmark report (10/10 pass rate)
└── engine/
    ├── Dockerfile              # Python 3.11 image with CPU PyTorch & cached embeddings
    ├── requirements.txt        # Engine Python dependencies
    ├── ingest.py               # PDF-to-Markdown parser, header chunker, and ChromaDB indexer
    ├── query.py                # Retrieval pipeline, XML prompt sandbox, and Ollama client
    ├── main.py                 # FastAPI application (/health and /audit endpoints)
    ├── eval.py                 # Automated functional & adversarial evaluation runner
    └── eval_cases.json         # 10 structured compliance and OWASP security test cases
```

---

## Quickstart

### Prerequisites

- **Docker Desktop** (with WSL 2 backend on Windows)
- **Ollama** installed and running on the host machine with `qwen3.5:4b` pulled:
  ```bash
  ollama pull qwen3.5:4b
  ```
- Python 3.11+ (for initial local PDF ingestion into `engine/chroma_db/`)

### 1. Clone & Index Policy Document

```bash
git clone [https://github.com/DiamondTDA/valsage-core.git](https://github.com/DiamondTDA/valsage-core.git)
cd valsage-core

# Place your policy PDF at docs/sample_policy.pdf, then build the local ChromaDB index:
python -m venv venv
venv\Scripts\activate          # On macOS/Linux: source venv/bin/activate
pip install -r engine/requirements.txt
python engine/ingest.py
```

### 2. Launch with Docker Compose

```bash
docker compose up --build -d
```

Verify that the container is up and healthy:

```bash
docker compose ps
```

Interactive Swagger UI documentation is available at **`http://127.0.0.1:8000/docs`**.

---

## API Reference

### `GET /health`

Returns service readiness status used by Docker's container healthcheck.

```json
{
  "status": "ok"
}
```

### `POST /audit`

Evaluates an employee inquiry against the indexed policy database.

**Request:**

```bash
curl -X POST "[http://127.0.0.1:8000/audit](http://127.0.0.1:8000/audit)" \
  -H "Content-Type: application/json" \
  -d '{"inquiry": "I spent $120 on dinner and hotel charges during my trip without keeping the receipts."}'
```

**Response (`200 OK`):**

```json
{
  "verdict": "VIOLATION",
  "cited_section": "Travel Reimbursement & Receipt Requirements",
  "reasoning": "Expenses of $50 or more require itemized receipts for reimbursement. Submitting a $120 dinner and hotel claim without valid receipts violates university travel policy."
}
```

---

## Roadmap (`v2.0`)

- [ ] **Enterprise Security Gateway Integration:** Connect a Spring Boot 3 gateway in front of `valsage-core` to handle JWT authentication, Role-Based Access Control (`EMPLOYEE`, `AUDITOR`, `ADMIN`), deterministic PII redaction, per-role rate limiting, and persistent audit logging.
