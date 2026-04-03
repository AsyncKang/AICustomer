import os
import re
from pathlib import Path
from typing import List

import fitz
import requests
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI()

QWEN_API_KEY = "sk-0a296d54eb7644b89d9510a14efa32ad"
QWEN_CHAT_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
QWEN_EMBEDDING_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"
QWEN_CHAT_MODEL = "qwen-plus"
QWEN_EMBEDDING_MODEL = "text-embedding-v2"
_DEFAULT_UPLOAD = Path(__file__).resolve().parent.parent / "backend" / "uploads"
UPLOAD_DIR = Path(os.getenv("UPLOAD_DIR", str(_DEFAULT_UPLOAD))).resolve()
MILVUS_URI = os.getenv("MILVUS_URI", "http://127.0.0.1:19530")
EMBED_DIM = 1536
REJECT_TEXT = "抱歉，该问题不在当前知识库服务范围内，请联系企业人工客服或提交工单获取帮助。"

_coll_cache: dict = {}


class AskRequest(BaseModel):
    tenant_id: int
    question: str


class IncrementalRequest(BaseModel):
    tenant_id: int
    doc_id: int
    file_path: str


class RebuildItem(BaseModel):
    doc_id: int
    file_path: str


class RebuildRequest(BaseModel):
    tenant_id: int
    items: List[RebuildItem]


def collection_name(tenant_id: int) -> str:
    return f"enterprise_kb_{tenant_id}"


def read_md(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def read_pdf(path: Path) -> str:
    doc = fitz.open(path)
    text = ""
    for page in doc:
        text += page.get_text()
    return text


def split_chunks(text: str, max_len: int = 500) -> List[str]:
    paragraphs = [p.strip() for p in text.split("\n\n") if p.strip()]
    if not paragraphs:
        t = text.strip()
        paragraphs = [t] if t else []
    chunks = []
    for p in paragraphs:
        if len(p) <= max_len:
            chunks.append(p)
        else:
            for i in range(0, len(p), max_len):
                chunks.append(p[i : i + max_len])
    return chunks


def question_keys(question: str) -> List[str]:
    q = re.sub(r"[\s\u3000，。！？、；：""''（）【】《》\n\r\t]", "", question)
    if not q:
        return []
    keys = []
    if len(q) <= 4:
        keys.append(q)
    else:
        for i in range(len(q) - 1):
            keys.append(q[i : i + 2])
    for w in re.findall(r"[A-Za-z0-9]+", question):
        if len(w) >= 2:
            keys.append(w)
    return list(dict.fromkeys(keys))


def simple_retrieve(question: str, tenant_id: int, top_k: int = 3) -> List[str]:
    if not UPLOAD_DIR.exists():
        return []
    tenant_dir = UPLOAD_DIR / str(tenant_id)
    if not tenant_dir.is_dir():
        return []
    scored = []
    keys = question_keys(question)
    fallback: List[str] = []
    for file in sorted(tenant_dir.iterdir()):
        if file.suffix.lower() not in [".md", ".pdf"]:
            continue
        text = read_md(file) if file.suffix.lower() == ".md" else read_pdf(file)
        for c in split_chunks(text):
            if len(fallback) < top_k * 2:
                fallback.append(c)
            if not keys:
                continue
            score = sum(1 for k in keys if k in c)
            if score > 0:
                scored.append((score, c))
    scored.sort(key=lambda x: x[0], reverse=True)
    picked = [x[1] for x in scored[:top_k]]
    if picked:
        return picked
    return fallback[:top_k]


def embed_texts(texts: List[str]) -> List[List[float]]:
    if not texts:
        return []
    headers = {"Authorization": f"Bearer {QWEN_API_KEY}", "Content-Type": "application/json"}
    payload = {"model": QWEN_EMBEDDING_MODEL, "input": texts}
    resp = requests.post(QWEN_EMBEDDING_URL, headers=headers, json=payload, timeout=120)
    resp.raise_for_status()
    data = resp.json()
    items = sorted(data["data"], key=lambda x: x["index"])
    return [item["embedding"] for item in items]


def _connect_milvus():
    from pymilvus import connections

    if connections.has_connection("default"):
        return
    connections.connect("default", uri=MILVUS_URI)


def _schema_fields():
    from pymilvus import DataType, FieldSchema

    return [
        FieldSchema(name="pk", dtype=DataType.INT64, is_primary=True, auto_id=True),
        FieldSchema(name="doc_id", dtype=DataType.INT64),
        FieldSchema(name="chunk_text", dtype=DataType.VARCHAR, max_length=16384),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=EMBED_DIM),
    ]


def _get_collection(tenant_id: int):
    if tenant_id in _coll_cache:
        return _coll_cache[tenant_id]
    from pymilvus import Collection, utility

    name = collection_name(tenant_id)
    _connect_milvus()
    if not utility.has_collection(name):
        return None
    coll = Collection(name)
    names = {f.name for f in coll.schema.fields}
    if "doc_id" not in names:
        utility.drop_collection(name)
        _coll_cache.pop(tenant_id, None)
        return None
    coll.load()
    _coll_cache[tenant_id] = coll
    return coll


def _create_empty_collection(tenant_id: int):
    from pymilvus import Collection, CollectionSchema, utility

    name = collection_name(tenant_id)
    _connect_milvus()
    if utility.has_collection(name):
        utility.drop_collection(name)
    _coll_cache.pop(tenant_id, None)
    fields = _schema_fields()
    schema = CollectionSchema(fields)
    return Collection(name, schema)


def _ensure_index_loaded(coll, tenant_id: int):
    coll.flush()
    index_params = {"metric_type": "COSINE", "index_type": "AUTOINDEX", "params": {}}
    try:
        coll.create_index(field_name="embedding", index_params=index_params)
    except Exception:
        pass
    coll.load()
    _coll_cache[tenant_id] = coll


def _ensure_collection_for_incremental(tenant_id: int):
    from pymilvus import Collection, utility

    name = collection_name(tenant_id)
    _connect_milvus()
    if utility.has_collection(name):
        coll = Collection(name)
        names = {f.name for f in coll.schema.fields}
        if "doc_id" not in names:
            utility.drop_collection(name)
            _coll_cache.pop(tenant_id, None)
        else:
            coll.load()
            _coll_cache[tenant_id] = coll
            return coll
    return _create_empty_collection(tenant_id)


@app.post("/api/index/incremental")
def incremental_index(req: IncrementalRequest):
    tenant_id = req.tenant_id
    file_path = Path(req.file_path).resolve()
    if not file_path.is_file():
        raise HTTPException(status_code=400, detail="file_not_found")
    try:
        file_path.relative_to(UPLOAD_DIR.resolve())
    except ValueError:
        raise HTTPException(status_code=400, detail="invalid_path")
    doc_id = req.doc_id
    coll = _ensure_collection_for_incremental(tenant_id)
    try:
        coll.delete(expr=f"doc_id == {doc_id}")
        coll.flush()
    except Exception:
        pass
    text = read_md(file_path) if file_path.suffix.lower() == ".md" else read_pdf(file_path)
    chunks = [c[:16380] for c in split_chunks(text) if c.strip()]
    if not chunks:
        coll.flush()
        return {"ok": True, "chunks": 0}
    batch_size = 16
    for i in range(0, len(chunks), batch_size):
        part = chunks[i : i + batch_size]
        doc_ids = [doc_id] * len(part)
        vecs = embed_texts(part)
        coll.insert([doc_ids, part, vecs])
    _ensure_index_loaded(coll, tenant_id)
    return {"ok": True, "chunks": len(chunks)}


@app.delete("/api/index/document/{tenant_id}/{doc_id}")
def delete_document_index(tenant_id: int, doc_id: int):
    coll = _get_collection(tenant_id)
    if coll is None:
        return {"ok": True}
    coll.delete(expr=f"doc_id == {doc_id}")
    coll.flush()
    return {"ok": True}


@app.post("/api/index/rebuild")
def rebuild(req: RebuildRequest):
    tid = req.tenant_id
    _coll_cache.pop(tid, None)
    from pymilvus import utility

    _connect_milvus()
    name = collection_name(tid)
    if utility.has_collection(name):
        utility.drop_collection(name)
    if not req.items:
        return {"ok": True, "chunks": 0}
    total = 0
    coll = _create_empty_collection(tid)
    for item in req.items:
        p = Path(item.file_path).resolve()
        if not p.is_file():
            continue
        text = read_md(p) if p.suffix.lower() == ".md" else read_pdf(p)
        chunks = [c[:16380] for c in split_chunks(text) if c.strip()]
        if not chunks:
            continue
        batch_size = 16
        for i in range(0, len(chunks), batch_size):
            part = chunks[i : i + batch_size]
            doc_ids = [item.doc_id] * len(part)
            vecs = embed_texts(part)
            coll.insert([doc_ids, part, vecs])
        total += len(chunks)
    _ensure_index_loaded(coll, tid)
    return {"ok": True, "chunks": total}


def vector_retrieve(question: str, tenant_id: int, top_k: int = 3) -> List[str]:
    try:
        coll = _get_collection(tenant_id)
        if coll is None or coll.num_entities == 0:
            return []
        qv = embed_texts([question.strip()[:8000]])[0]
        res = coll.search(
            data=[qv],
            anns_field="embedding",
            param={"metric_type": "COSINE", "params": {}},
            limit=top_k,
            output_fields=["chunk_text"],
        )
        out: List[str] = []
        for hits in res:
            for hit in hits:
                t = None
                try:
                    t = hit.get("chunk_text")
                except Exception:
                    pass
                if t is None and hit.entity is not None:
                    e = hit.entity
                    t = e.get("chunk_text") if hasattr(e, "get") else getattr(e, "chunk_text", None)
                if t:
                    out.append(t)
        return out[:top_k]
    except Exception:
        return []


def retrieve(question: str, tenant_id: int, top_k: int = 3) -> List[str]:
    vec = vector_retrieve(question, tenant_id, top_k)
    if vec:
        return vec
    return simple_retrieve(question, tenant_id, top_k)


def build_prompt(context: str, question: str) -> str:
    return f"""你是企业官方智能客服助手，仅依据企业提供的知识库为用户解答与业务相关的问题，严格遵守以下规则：
1. 仅依据下方「参考资料」回答；参考资料来自企业上传的正式文档。
2. 若参考资料无相关内容，或用户问题与企业业务、产品/服务无关，必须直接回复：“{REJECT_TEXT}”
3. 不得编造、联想、使用外部知识回答；不得泄露未公开或敏感信息。
4. 回答简洁、专业、礼貌，符合企业服务规范。
5. 涉及政策、价格、时效、联系方式等，须与参考资料一致，不得自行发挥。
参考资料：
{context}
用户问题：{question}"""


def call_qwen(prompt: str) -> str:
    if not QWEN_API_KEY:
        return REJECT_TEXT
    headers = {"Authorization": f"Bearer {QWEN_API_KEY}", "Content-Type": "application/json"}
    payload = {
        "model": QWEN_CHAT_MODEL,
        "messages": [{"role": "user", "content": prompt}],
        "temperature": 0.1,
    }
    try:
        resp = requests.post(QWEN_CHAT_URL, headers=headers, json=payload, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        return data["choices"][0]["message"]["content"]
    except Exception:
        return "系统暂时维护，请稍后再试"


@app.post("/api/ask")
def ask(req: AskRequest):
    chunks = retrieve(req.question, req.tenant_id, 3)
    if not chunks:
        return {"answer": REJECT_TEXT}
    context = "\n\n".join(chunks)
    prompt = build_prompt(context, req.question)
    return {"answer": call_qwen(prompt)}


@app.get("/health")
def health():
    status = "UP"
    milvus_status = "UNKNOWN"
    try:
        _connect_milvus()
        milvus_status = "UP"
    except Exception:
        milvus_status = "DOWN"
        status = "DOWN"
    llm = "CONFIGURED" if QWEN_API_KEY else "MISSING"
    return {"status": status, "milvus": milvus_status, "llm": llm}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=True)
