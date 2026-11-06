from pathlib import Path
import chromadb
from chromadb.utils import embedding_functions

db_path = Path(__file__).resolve().parent / "chroma_db"
chroma_Client = chromadb.PersistentClient(path = str(db_path))
chroma_Collection = chroma_Client.get_collection(name = 'compliance_docs')

print(chroma_Collection.get(limit = 3))