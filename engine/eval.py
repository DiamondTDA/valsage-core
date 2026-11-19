import json
import time
from datetime import datetime
from pathlib import Path
import numpy as np
from chromadb.utils.embedding_functions import SentenceTransformerEmbeddingFunction
from query import audit_inquiry

BASE_DIR = Path(__file__).resolve().parent
CASES_FILE = BASE_DIR / "eval_cases.json"
DOCS_DIR = BASE_DIR.parent / "docs"
REPORT_FILE = DOCS_DIR / "EVALUATION.md"
SIMILARITY_THRESHOLD = 0.50


def cosine_similarity(embed_fn, text_a: str, text_b: str) -> float:
    vecs = embed_fn([text_a, text_b])
    u, v = np.array(vecs[0]), np.array(vecs[1])
    denom = np.linalg.norm(u) * np.linalg.norm(v)
    return float((u @ v) / denom) if denom > 0 else 0.0


def run_evaluations():
    DOCS_DIR.mkdir(parents=True, exist_ok=True)
    embed_fn = SentenceTransformerEmbeddingFunction(model_name="all-MiniLM-L6-v2")

    with open(CASES_FILE, "r", encoding="utf-8") as f:
        test_cases = json.load(f)

    markdown_rows = []
    passed_count = 0
    total_latency = 0.0

    print("=" * 75)
    print("RUNNING VALSAGE STRUCTURED COMPLIANCE EVALUATION SUITE")
    print("=" * 75)

    for case in test_cases:
        tc_id = case["id"]
        category = case["category"]
        inquiry = case["inquiry"]
        expected_verdict = case["expected_verdict"]
        ref_reasoning = case["reference_reasoning"]

        print(f"\n[{tc_id}] {category}")

        start_time = time.perf_counter()
        result = audit_inquiry(inquiry)
        elapsed_sec = time.perf_counter() - start_time
        total_latency += elapsed_sec

        parsed = result["parsed"]
        actual_verdict = parsed.get("verdict", "MISSING")
        actual_reasoning = parsed.get("reasoning", "")

        eval_count = result.get("eval_count", 0)
        eval_duration_sec = result.get("eval_duration", 1) / 1e9
        tps = eval_count / eval_duration_sec if eval_duration_sec > 0 else 0.0

        sim_score = cosine_similarity(embed_fn, actual_reasoning, ref_reasoning)

        reasons = []
        if actual_verdict != expected_verdict:
            reasons.append(f"Expected `{expected_verdict}`, got `{actual_verdict}`")
        if sim_score < SIMILARITY_THRESHOLD:
            reasons.append(f"Low reasoning similarity (`{sim_score:.2f}` < `{SIMILARITY_THRESHOLD}`)")

        passed = len(reasons) == 0
        if passed:
            passed_count += 1
            status_badge = "PASS"
            failure_col = "—"
        else:
            status_badge = "FAIL"
            failure_col = "; ".join(reasons)

        print(f"  Result: {status_badge} | Verdict: {actual_verdict} | Sim: {sim_score:.2f} | {elapsed_sec:.2f}s ({tps:.1f} tok/s)")
        if not passed:
            print(f"  -> Failure Reason: {failure_col}")

        clean_reasoning = actual_reasoning.replace("\n", " ").replace("|", "/")
        if len(clean_reasoning) > 110:
            clean_reasoning = clean_reasoning[:107] + "..."

        markdown_rows.append(
            f"| **{tc_id}** | {category} | **{status_badge}** | `{actual_verdict}` | `{sim_score:.2f}` | `{elapsed_sec:.2f}s` | {failure_col} | {clean_reasoning} |"
        )

    avg_latency = total_latency / len(test_cases) if test_cases else 0.0
    pass_rate = (passed_count / len(test_cases) * 100) if test_cases else 0.0

    report_content = f"""# ValSage AI Engine — Evaluation & Benchmark Report

* **Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
* **Inference Model:** `qwen3.5:4b` (Local Ollama, Structured JSON Schema, `temperature=0`)
* **Embedding Model:** `all-MiniLM-L6-v2` (ChromaDB Persistent Store)
* **Pass Rate:** **{passed_count}/{len(test_cases)} ({pass_rate:.0f}%)**
* **Average End-to-End Latency:** `{avg_latency:.2f}s`

## Benchmark Results

| Test ID | Category | Status | Verdict | Sim Score | Latency | Failure Reason | Reasoning Snippet |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
{chr(10).join(markdown_rows)}

## Grading Methodology
* **Verdict Gate (Deterministic):** Exact string equality against the schema-enforced `verdict` (`COMPLIANT`, `VIOLATION`, `OUT_OF_BOUNDS`).
* **Semantic Gate (Embeddings):** Cosine similarity between Qwen's `reasoning` and the golden `reference_reasoning` using `all-MiniLM-L6-v2` (threshold `>= {SIMILARITY_THRESHOLD}`).
"""

    with open(REPORT_FILE, "w", encoding="utf-8") as f:
        f.write(report_content)

    print("\n" + "=" * 75)
    print(f"SUMMARY: {passed_count}/{len(test_cases)} Passed ({pass_rate:.0f}%) | Avg Latency: {avg_latency:.2f}s")
    print(f"Report saved to: {REPORT_FILE}")
    print("=" * 75)


if __name__ == "__main__":
    run_evaluations()