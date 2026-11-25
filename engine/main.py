from pydantic import BaseModel, Field
from fastapi import FastAPI, status, HTTPException
import requests
from query import audit_inquiry

app = FastAPI(title = "ValSage Compliance Engine", version= "1.0")

class AuditRequest(BaseModel):
    inquiry: str = Field(..., min_length = 1, description="Employee travel inquiry or claim")

class AuditResponse(BaseModel):
    verdict: str
    cited_section: str
    reasoning: str


@app.get("/health", status_code=status.HTTP_200_OK)
def health_check():
    return{"status":"ok"}

@app.post("/audit", response_model = AuditResponse, status_code= status.HTTP_200_OK)
def run_audit(request: AuditRequest):
    try:
        result = audit_inquiry(request.inquiry)
        return result['parsed']
    except requests.RequestException as exc:
        raise HTTPException(
            status_code = status.HTTP_503_SERVICE_UNAVAILABLE,
            detail = f"Local LLM inference service unavailable: {exc}"
        ) from exc