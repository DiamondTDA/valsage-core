#Imported packages
import pymupdf4llm
import hashlib
from pathlib import Path
from langchain_text_splitters import MarkdownHeaderTextSplitter, RecursiveCharacterTextSplitter
import chromadb
from chromadb.utils import embedding_functions

#Defining path and hashing the file for change detection.
document_Path = Path(__file__).resolve().parent.parent / "docs" / "sample_policy.pdf"
hash_file = Path(__file__).resolve().parent / ".policy_hash"
file_bytes = document_Path.read_bytes()
current_hash = hashlib.md5(file_bytes).hexdigest()

if hash_file.exists() and hash_file.read_text().strip() == current_hash:
    print(f"Database already up to date.")
else:
    #Importing the file as markdown
    document = pymupdf4llm.to_markdown(document_Path)

    #Splitting the file into chunks
    headers = [("#", "H1"), ("##", "H2"), ("###", "H3")]
    header_splitter = MarkdownHeaderTextSplitter(
        headers_to_split_on = headers,
        strip_headers = False
    )
    sections = header_splitter.split_text(document)
    char_splitter = RecursiveCharacterTextSplitter(chunk_size =700 , chunk_overlap = 80)
    chunks = char_splitter.split_documents(sections)

    #ChromaDB DataBase Setup
    db_path = Path(__file__).resolve().parent / "chroma_db"
    chroma_Client = chromadb.PersistentClient(path = str(db_path))
    embedding_fn = embedding_functions.SentenceTransformerEmbeddingFunction(model_name="all-MiniLM-L6-v2")
    chroma_Collection = chroma_Client.get_or_create_collection(name = "compliance_docs", embedding_function= embedding_fn)
    db_count = chroma_Collection.count()
    chroma_Collection.add(
        documents = [c.page_content for c in chunks],
        metadatas = [c.metadata for c in chunks],
        ids = [f"policy_chunk_{i}" for i in range(len(chunks))]
    )
    print(f"Detected change (DB has {db_count}, incoming has {len(chunks)}). Updating collection...")

    #Hashing the current file.
    hash_file.write_text(current_hash)