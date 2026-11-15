import hashlib
from pathlib import Path
import chromadb
from chromadb.utils import embedding_functions
from langchain_text_splitters import MarkdownHeaderTextSplitter, RecursiveCharacterTextSplitter
import pymupdf4llm

BASE_DIR = Path(__file__).resolve().parent
DOCUMENT_PATH = BASE_DIR.parent / "docs" / "sample_policy.pdf"
DB_PATH = BASE_DIR / "chroma_db"
HASH_FILE = BASE_DIR / ".policy_hash"
COLLECTION_NAME = "compliance_docs"
EMBEDDING_MODEL_NAME = "all-MiniLM-L6-v2"


def ingest_policy() -> None:
    file_bytes = DOCUMENT_PATH.read_bytes()
    current_hash = hashlib.md5(file_bytes).hexdigest()

    if HASH_FILE.exists() and HASH_FILE.read_text().strip() == current_hash:
        print("Database already up to date.")
        return

    # Import the PDF as markdown
    document = pymupdf4llm.to_markdown(DOCUMENT_PATH)

    # Split the markdown into semantic header sections and fixed chunks
    headers = [("#", "H1"), ("##", "H2"), ("###", "H3")]
    header_splitter = MarkdownHeaderTextSplitter(
        headers_to_split_on=headers,
        strip_headers=False,
    )
    sections = header_splitter.split_text(document)
    char_splitter = RecursiveCharacterTextSplitter(chunk_size=700, chunk_overlap=80)
    chunks = char_splitter.split_documents(sections)

    # ChromaDB database setup
    chroma_client = chromadb.PersistentClient(path=str(DB_PATH))
    embedding_fn = embedding_functions.SentenceTransformerEmbeddingFunction(
        model_name=EMBEDDING_MODEL_NAME
    )
    chroma_collection = chroma_client.get_or_create_collection(
        name=COLLECTION_NAME,
        embedding_function=embedding_fn,
    )
    db_count = chroma_collection.count()
    chroma_collection.upsert(
        documents=[c.page_content for c in chunks],
        metadatas=[c.metadata for c in chunks],
        ids=[f"policy_chunk_{i}" for i in range(len(chunks))],
    )
    print(f"Detected change (DB has {db_count}, incoming has {len(chunks)}). Updating collection...")

    # Save the hash of the indexed file
    HASH_FILE.write_text(current_hash)


if __name__ == "__main__":
    ingest_policy()