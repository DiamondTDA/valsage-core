from pathlib import Path
import chromadb
from chromadb.utils import embedding_functions

DB_PATH = Path(__file__).resolve().parent / "chroma_db"
chroma_client = chromadb.PersistentClient(path = str(DB_PATH))
chroma_collection = chroma_client.get_collection(name = 'compliance_docs')

print(chroma_collection.get(limit = 3))