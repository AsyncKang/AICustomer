# 企业智能客服（AICustomer）

仓库：<https://github.com/AsyncKang/AICustomer>

基于私有知识库的 RAG 问答系统：支持多租户、文档上传与向量索引、智能对话与历史记录。前端为 Vue 3，后端为 Spring Boot，AI 与检索服务为 FastAPI（通义千问 + Milvus）。

## 技术栈

| 模块 | 说明 |
|------|------|
| `frontend/` | Vue 3、Vite |
| `backend/` | Spring Boot 3、JPA、Redis、JWT、Resilience4j |
| `ai-service/` | FastAPI、PyMuPDF、PyMilvus、DashScope API |

依赖中间件：**MySQL**、**Redis**、**Milvus**。

## 目录结构

```
├── backend/          # Java API、鉴权、文档与索引队列
├── frontend/         # 管理端与对话界面
├── ai-service/       # 问答、增量索引、向量检索
└── README.md
```

## 环境要求

- JDK 17+
- Node.js 18+（前端）
- Python 3.9+（ai-service）
- MySQL 8、Redis、Milvus 2.x

## 配置说明

1. **MySQL**：创建数据库（默认名见 `backend/src/main/resources/application.yml` 中 `spring.datasource.url`），首次启动由 JPA `ddl-auto: update` 建表。
2. **后端**：修改 `application.yml` 中的数据源、Redis、`app.jwt.secret`（生产环境务必更换）、各 `app.python-*-url` 指向实际 AI 服务地址。
3. **AI 服务**：在 `ai-service` 中配置环境变量或代码中的 `QWEN_API_KEY`、`MILVUS_URI`、`UPLOAD_DIR`（需与 Java 上传目录一致或指向可访问路径）。

## 本地启动（示例）

**1. Milvus、MySQL、Redis** 按官方方式先启动。

**2. AI 服务**

```bash
cd ai-service
python -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 8000
```

**3. 后端**

```bash
cd backend
mvn spring-boot:run
```

默认 HTTP 端口：`8080`。

**4. 前端**

```bash
cd frontend
npm install
npm run dev
```

默认开发端口：`5173`。前端内 API 基址见 `src/App.vue` 中 `apiBase`，需与后端一致。

## 主要功能

- 用户注册 / 登录（租户编码、角色：访客 / 管理员）
- 智能对话（缓存、熔断、限流）
- 知识库：Markdown/PDF 上传、索引状态、重建索引、下载与覆盖上传
- 会话历史、Actuator 健康与 Prometheus 指标

## 许可证

私有项目使用时请自行补充许可证信息。
