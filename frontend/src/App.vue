<script setup>
import { ref, computed, nextTick, watch } from 'vue'

const apiBase = 'http://127.0.0.1:8080'
const user = ref(null)
const authView = ref('login')
const loginForm = ref({ username: '', password: '', tenantCode: '' })
const registerForm = ref({ username: '', password: '', tenantCode: '', role: 'user' })
const question = ref('')
const messages = ref([])
const sending = ref(false)
const file = ref(null)
const category = ref('')
const documents = ref([])
const logs = ref([])
const activeTab = ref('chat')
const toast = ref('')
const chatScroll = ref(null)
const token = ref('')

function showToast(text) {
  toast.value = text
  setTimeout(() => { toast.value = '' }, 3200)
}

const isAdmin = computed(() => user.value?.role === 'admin')

function authHeaders() {
  return token.value ? { Authorization: `Bearer ${token.value}` } : {}
}

async function scrollChatToEnd() {
  await nextTick()
  const el = chatScroll.value
  if (el) el.scrollTop = el.scrollHeight
}

watch(messages, () => scrollChatToEnd(), { deep: true })

async function register() {
  const f = registerForm.value
  if (!f.username.trim() || !f.password || !f.tenantCode.trim() || !f.role) {
    showToast('请填写全部字段')
    return
  }
  try {
    const res = await fetch(`${apiBase}/api/auth/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: f.username.trim(),
        password: f.password,
        tenantCode: f.tenantCode.trim(),
        role: f.role
      })
    })
    const data = await res.json()
    if (!res.ok) throw new Error(data.message || '注册失败')
    user.value = data
    token.value = data.token || ''
    sessionStorage.setItem('cs_user', JSON.stringify(data))
    messages.value = []
    showToast('注册成功')
    await loadLogs()
    if (data.role === 'admin') await loadDocs()
  } catch (e) {
    showToast(e.message || '注册失败')
  }
}

async function login() {
  const f = loginForm.value
  if (!f.username.trim() || !f.password || !f.tenantCode.trim()) {
    showToast('请填写全部字段')
    return
  }
  try {
    const res = await fetch(`${apiBase}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: f.username.trim(),
        password: f.password,
        tenantCode: f.tenantCode.trim()
      })
    })
    const data = await res.json()
    if (!res.ok) throw new Error(data.message || '登录失败')
    user.value = data
    token.value = data.token || ''
    sessionStorage.setItem('cs_user', JSON.stringify(data))
    messages.value = []
    activeTab.value = 'chat'
    showToast('欢迎回来')
    await loadLogs()
    if (data.role === 'admin') await loadDocs()
  } catch (e) {
    showToast(e.message || '登录失败')
  }
}

function logout() {
  user.value = null
  token.value = ''
  sessionStorage.removeItem('cs_user')
  messages.value = []
  logs.value = []
  documents.value = []
}

function restoreSession() {
  const raw = sessionStorage.getItem('cs_user')
  if (!raw) return
  try {
    user.value = JSON.parse(raw)
    token.value = user.value?.token || ''
    loadLogs()
    if (user.value?.role === 'admin') loadDocs()
  } catch {
    sessionStorage.removeItem('cs_user')
  }
}

restoreSession()

async function ask() {
  if (!user.value || !question.value.trim() || sending.value) return
  const q = question.value.trim()
  question.value = ''
  messages.value.push({ role: 'user', content: q, t: Date.now() })
  sending.value = true
  try {
    const res = await fetch(`${apiBase}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', ...authHeaders() },
      body: JSON.stringify({ question: q })
    })
    const data = await res.json()
    if (!res.ok) {
      messages.value.push({ role: 'assistant', content: data.message || '请求失败', t: Date.now() })
      showToast(data.message || '请求失败')
      return
    }
    messages.value.push({ role: 'assistant', content: data.answer || '', t: Date.now() })
    await loadLogs()
  } catch (e) {
    messages.value.push({ role: 'assistant', content: '暂时无法获取回答，请稍后重试。', t: Date.now() })
    showToast(e.message || '发送失败')
  } finally {
    sending.value = false
  }
}

function onAskKey(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    ask()
  }
}

async function upload(overwrite = false) {
  if (!file.value || !user.value) return
  const form = new FormData()
  form.append('file', file.value)
  form.append('category', category.value)
  form.append('overwrite', String(overwrite))
  try {
    const res = await fetch(`${apiBase}/api/documents/upload`, { method: 'POST', headers: { ...authHeaders() }, body: form })
    const data = await res.json().catch(() => ({}))
    if (!res.ok) {
      const msg = data.message || ''
      if (msg.includes('覆盖') && !overwrite) {
        if (confirm('同名同类型文件已存在，是否覆盖？')) {
          return upload(true)
        }
        return
      }
      throw new Error(msg || '上传失败')
    }
    file.value = null
    category.value = ''
    showToast('文档已上传')
    await loadDocs()
  } catch (e) {
    showToast(e.message || '上传失败')
  }
}

async function downloadDoc(doc) {
  if (!user.value) return
  try {
    const res = await fetch(`${apiBase}/api/documents/${doc.id}/download`, { headers: { ...authHeaders() } })
    if (!res.ok) {
      const data = await res.json().catch(() => ({}))
      throw new Error(data.message || '下载失败')
    }
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = doc.name
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    showToast(e.message || '下载失败')
  }
}

function onFileChange(e) {
  file.value = e.target.files?.[0] || null
}

async function loadDocs() {
  try {
    const res = await fetch(`${apiBase}/api/documents`, { headers: { ...authHeaders() } })
    documents.value = await res.json()
  } catch {
    documents.value = []
  }
}

async function removeDoc(id) {
  if (!user.value) return
  try {
    const res = await fetch(`${apiBase}/api/documents/${id}`, { method: 'DELETE', headers: { ...authHeaders() } })
    const data = await res.json().catch(() => ({}))
    if (!res.ok) throw new Error(data.message || '删除失败')
    showToast('已删除')
    await loadDocs()
  } catch (e) {
    showToast(e.message || '删除失败')
  }
}

function indexStatusLabel(s) {
  const m = {
    UPLOADED: '已上传',
    PARSED: '已解析',
    EMBEDDED: '已向量化',
    INDEXED: '已索引',
    FAILED: '失败'
  }
  return m[s] || s || ''
}

function truncateErr(s, n) {
  if (!s) return ''
  return s.length > n ? s.slice(0, n) + '…' : s
}

async function retryIndexDoc(id) {
  if (!user.value) return
  try {
    const res = await fetch(`${apiBase}/api/documents/${id}/retry-index`, { method: 'POST', headers: { ...authHeaders() } })
    const data = await res.json().catch(() => ({}))
    if (!res.ok) throw new Error(data.message || '重试失败')
    showToast('已重新入队')
    await loadDocs()
  } catch (e) {
    showToast(e.message || '重试失败')
  }
}

async function rebuildKb() {
  if (!user.value) return
  try {
    const res = await fetch(`${apiBase}/api/documents/rebuild-index`, { method: 'POST', headers: { ...authHeaders() } })
    const data = await res.json().catch(() => ({}))
    if (!res.ok) throw new Error(data.message || '重建失败')
    showToast('索引已重建')
    await loadDocs()
  } catch (e) {
    showToast(e.message || '重建失败')
  }
}

async function loadLogs() {
  if (!user.value) return
  try {
    const res = await fetch(`${apiBase}/api/chat/logs/me`, { headers: { ...authHeaders() } })
    logs.value = await res.json()
  } catch {
    logs.value = []
  }
}

function formatTime(ts) {
  if (ts == null) return ''
  let d
  if (typeof ts === 'string') {
    d = new Date(ts)
  } else if (Array.isArray(ts) && ts.length >= 5) {
    const [y, mo, day, h, mi] = ts
    d = new Date(y, mo - 1, day, h, mi, ts[5] || 0)
  } else if (typeof ts === 'number') {
    d = new Date(ts)
  } else {
    return ''
  }
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getMonth() + 1}/${d.getDate()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function tabClass(id) {
  return activeTab.value === id ? 'nav-item active' : 'nav-item'
}
</script>

<template>
  <div class="shell">
    <div v-if="toast" class="toast">{{ toast }}</div>

    <template v-if="!user">
      <div class="login-bg">
        <div class="login-card">
          <div class="login-brand">
            <div class="logo-mark">企</div>
            <div>
              <h1>企业智能客服</h1>
              <p class="sub">私有知识库 · RAG 问答</p>
            </div>
          </div>
          <template v-if="authView === 'login'">
            <div class="form-grid">
              <label class="field">
                <span>用户名</span>
                <input v-model="loginForm.username" type="text" placeholder="请输入用户名" autocomplete="username" />
              </label>
              <label class="field">
                <span>密码</span>
                <input v-model="loginForm.password" type="password" placeholder="请输入密码" autocomplete="current-password" />
              </label>
              <label class="field">
                <span>租户编码</span>
                <input v-model="loginForm.tenantCode" type="text" placeholder="请输入租户编码" autocomplete="off" />
              </label>
            </div>
            <div class="login-actions">
              <button type="button" class="btn primary" @click="login">登录</button>
            </div>
            <p class="auth-switch"><button type="button" class="link" @click="authView = 'register'">没有账号？注册</button></p>
          </template>
          <template v-else>
            <div class="form-grid">
              <label class="field">
                <span>用户名</span>
                <input v-model="registerForm.username" type="text" placeholder="请输入用户名" autocomplete="username" />
              </label>
              <label class="field">
                <span>密码</span>
                <input v-model="registerForm.password" type="password" placeholder="请输入密码" autocomplete="new-password" />
              </label>
              <label class="field">
                <span>租户编码</span>
                <input v-model="registerForm.tenantCode" type="text" placeholder="同企业填同一编码" autocomplete="off" />
              </label>
              <label class="field">
                <span>角色</span>
                <select v-model="registerForm.role">
                  <option value="user">访客</option>
                  <option value="admin">管理员</option>
                </select>
              </label>
            </div>
            <div class="login-actions">
              <button type="button" class="btn primary" @click="register">注册</button>
            </div>
            <p class="auth-switch"><button type="button" class="link" @click="authView = 'login'">已有账号？登录</button></p>
          </template>
          <p class="hint">登录后可使用智能问答；管理员可上传与维护企业知识库文档。</p>
        </div>
      </div>
    </template>

    <template v-else>
      <aside class="sidebar">
        <div class="side-brand">
          <div class="logo-mark sm">企</div>
          <div>
            <div class="brand-title">智能客服</div>
            <div class="brand-sub">Enterprise · CS</div>
          </div>
        </div>
        <nav class="side-nav">
          <button type="button" :class="tabClass('chat')" @click="activeTab = 'chat'">
            <span class="nav-ico">◇</span>
            智能对话
          </button>
          <button v-if="isAdmin" type="button" :class="tabClass('kb')" @click="activeTab = 'kb'; loadDocs()">
            <span class="nav-ico">▤</span>
            知识库
          </button>
          <button type="button" :class="tabClass('history')" @click="activeTab = 'history'; loadLogs()">
            <span class="nav-ico">☰</span>
            会话记录
          </button>
        </nav>
        <div class="side-user">
          <div class="avatar">{{ user.username?.slice(0, 1) || 'U' }}</div>
          <div class="side-user-meta">
            <div class="uname">{{ user.username }}</div>
            <div class="urole">{{ user.role === 'admin' ? '管理员' : '访客' }} · {{ user.tenantCode || 'default' }}</div>
          </div>
          <button type="button" class="btn-text" @click="logout">退出</button>
        </div>
      </aside>

      <div class="main">
        <header class="topbar">
          <div class="topbar-title">
            <template v-if="activeTab === 'chat'">智能对话</template>
            <template v-else-if="activeTab === 'kb'">知识库管理</template>
            <template v-else>会话记录</template>
          </div>
          <div class="topbar-badge">在线</div>
        </header>

        <div v-show="activeTab === 'chat'" class="panel chat-panel">
          <div ref="chatScroll" class="chat-stream">
            <div v-if="messages.length === 0" class="empty-chat">
              <h3>开始对话</h3>
              <p>您可以咨询产品、订单、政策等企业公开信息。回答依据知识库文档生成。</p>
            </div>
            <div
              v-for="(m, i) in messages"
              :key="i"
              class="msg"
              :class="m.role"
            >
              <div class="msg-bubble">
                <div class="msg-text">{{ m.content }}</div>
                <div class="msg-time">{{ formatTime(m.t) }}</div>
              </div>
            </div>
            <div v-if="sending" class="msg assistant">
              <div class="msg-bubble typing">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
          <div class="composer">
            <textarea
              v-model="question"
              rows="1"
              placeholder="输入您的问题，Enter 发送，Shift+Enter 换行"
              @keydown="onAskKey"
            />
            <button type="button" class="btn send" :disabled="sending" @click="ask">发送</button>
          </div>
        </div>

        <div v-show="activeTab === 'kb' && isAdmin" class="panel kb-panel">
          <div class="upload-card">
            <div class="upload-head">
              <h3>上传文档</h3>
              <p>支持 Markdown 与 PDF，用于企业知识库与对外资料检索。</p>
            </div>
            <div class="upload-row">
              <label class="file-pill">
                <input type="file" accept=".md,.pdf" @change="onFileChange" />
                <span>{{ file ? file.name : '选择文件' }}</span>
              </label>
              <input v-model="category" class="cat-input" type="text" placeholder="分类标签（如：产品说明 / 售后政策）" />
              <button type="button" class="btn primary" @click="upload">上传</button>
            </div>
          </div>
          <div class="table-card">
            <div class="table-head">
              <span>已入库文档</span>
              <div class="table-actions">
                <button type="button" class="btn ghost sm" @click="rebuildKb">重建索引</button>
                <button type="button" class="btn ghost sm" @click="loadDocs">刷新</button>
              </div>
            </div>
            <table class="data-table">
              <thead>
                <tr>
                  <th>名称</th>
                  <th>分类</th>
                  <th>类型</th>
                  <th>索引状态</th>
                  <th>错误</th>
                  <th width="180"></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="doc in documents" :key="doc.id">
                  <td>{{ doc.name }}</td>
                  <td><span class="tag">{{ doc.category }}</span></td>
                  <td>{{ doc.fileType }}</td>
                  <td>{{ indexStatusLabel(doc.indexStatus) }}</td>
                  <td class="err-cell">{{ truncateErr(doc.indexError, 48) }}</td>
                  <td>
                    <button type="button" class="link" @click="downloadDoc(doc)">下载</button>
                    <button v-if="doc.indexStatus === 'FAILED'" type="button" class="link" @click="retryIndexDoc(doc.id)">重试</button>
                    <button type="button" class="link danger" @click="removeDoc(doc.id)">删除</button>
                  </td>
                </tr>
                <tr v-if="documents.length === 0">
                  <td colspan="6" class="empty-cell">暂无文档</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <div v-show="activeTab === 'history'" class="panel history-panel">
          <div class="table-card">
            <div class="table-head">
              <span>历史会话</span>
              <button type="button" class="btn ghost sm" @click="loadLogs">刷新</button>
            </div>
            <div class="history-list">
              <div v-for="item in logs" :key="item.id" class="history-item">
                <div class="history-q">
                  <span class="badge user">问</span>
                  {{ item.question }}
                </div>
                <div class="history-a">
                  <span class="badge bot">答</span>
                  {{ item.answer }}
                </div>
                <div class="history-meta">{{ formatTime(item.createdAt) }}</div>
              </div>
              <div v-if="logs.length === 0" class="empty-cell flat">暂无记录</div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style>
:root {
  --bg: #f1f5f9;
  --surface: #ffffff;
  --sidebar: #0f172a;
  --sidebar-hover: #1e293b;
  --text: #0f172a;
  --muted: #64748b;
  --line: #e2e8f0;
  --primary: #2563eb;
  --primary-hover: #1d4ed8;
  --user-bubble: #eff6ff;
  --bot-bubble: #f8fafc;
  --radius: 12px;
  --shadow: 0 1px 3px rgba(15, 23, 42, 0.06), 0 8px 24px rgba(15, 23, 42, 0.06);
  --font: 'Noto Sans SC', system-ui, sans-serif;
}

* {
  box-sizing: border-box;
}

html, body, #app {
  height: 100%;
  margin: 0;
}

body {
  font-family: var(--font);
  color: var(--text);
  background: var(--bg);
  -webkit-font-smoothing: antialiased;
}

.shell {
  min-height: 100%;
}

.toast {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  padding: 10px 20px;
  background: #0f172a;
  color: #fff;
  border-radius: 999px;
  font-size: 14px;
  box-shadow: var(--shadow);
}

.login-bg {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background:
    radial-gradient(ellipse 80% 60% at 20% 10%, rgba(37, 99, 235, 0.12), transparent),
    radial-gradient(ellipse 60% 50% at 80% 90%, rgba(14, 165, 233, 0.1), transparent),
    linear-gradient(165deg, #f8fafc 0%, #e2e8f0 100%);
}

.login-card {
  width: 100%;
  max-width: 420px;
  background: var(--surface);
  border-radius: 20px;
  padding: 36px 32px 28px;
  box-shadow: var(--shadow);
  border: 1px solid var(--line);
}

.login-brand {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-bottom: 28px;
}

.login-brand h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.login-brand .sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--muted);
}

.logo-mark {
  width: 52px;
  height: 52px;
  border-radius: 14px;
  background: linear-gradient(135deg, var(--primary), #0ea5e9);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 22px;
}

.logo-mark.sm {
  width: 40px;
  height: 40px;
  font-size: 18px;
  border-radius: 10px;
}

.form-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field span {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--muted);
  margin-bottom: 6px;
}

.field input,
.field select {
  width: 100%;
  padding: 11px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  font-size: 15px;
  font-family: inherit;
  background: #fff;
  transition: border-color 0.15s, box-shadow 0.15s;
}

.field input:focus,
.field select:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

.login-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}

.auth-switch {
  margin: 16px 0 0;
  text-align: center;
}

.auth-switch .link {
  background: none;
  border: none;
  color: var(--primary);
  cursor: pointer;
  font-size: 14px;
  font-family: inherit;
  padding: 0;
}

.auth-switch .link:hover {
  text-decoration: underline;
}

.btn {
  font-family: inherit;
  font-size: 15px;
  font-weight: 500;
  padding: 11px 20px;
  border-radius: 10px;
  border: none;
  cursor: pointer;
  transition: background 0.15s, transform 0.1s;
}

.btn:active {
  transform: scale(0.98);
}

.btn.primary {
  flex: 1;
  background: var(--primary);
  color: #fff;
}

.btn.primary:hover:not(:disabled) {
  background: var(--primary-hover);
}

.btn.primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn.ghost {
  flex: 1;
  background: #f1f5f9;
  color: var(--text);
}

.btn.ghost:hover {
  background: #e2e8f0;
}

.btn.ghost.sm {
  flex: none;
  padding: 6px 14px;
  font-size: 13px;
}

.btn.send {
  padding: 12px 22px;
  background: var(--primary);
  color: #fff;
  align-self: flex-end;
}

.btn.send:hover:not(:disabled) {
  background: var(--primary-hover);
}

.btn-text {
  background: none;
  border: none;
  color: #94a3b8;
  font-size: 13px;
  cursor: pointer;
  padding: 4px 0;
}

.btn-text:hover {
  color: #e2e8f0;
}

.hint {
  margin: 20px 0 0;
  font-size: 12px;
  color: var(--muted);
  text-align: center;
  line-height: 1.5;
}

.sidebar {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: 240px;
  background: var(--sidebar);
  color: #e2e8f0;
  display: flex;
  flex-direction: column;
  padding: 20px 14px;
  z-index: 10;
}

.side-brand {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 8px 8px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-title {
  font-weight: 600;
  font-size: 15px;
}

.brand-sub {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}

.side-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 0;
  flex: 1;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #94a3b8;
  font-size: 14px;
  font-family: inherit;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s, color 0.15s;
}

.nav-item:hover {
  background: var(--sidebar-hover);
  color: #e2e8f0;
}

.nav-item.active {
  background: rgba(37, 99, 235, 0.2);
  color: #fff;
}

.nav-ico {
  opacity: 0.85;
  font-size: 12px;
}

.side-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 8px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #334155, #475569);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  font-size: 14px;
  color: #fff;
}

.side-user-meta {
  flex: 1;
  min-width: 0;
}

.uname {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.urole {
  font-size: 11px;
  color: #64748b;
}

.main {
  margin-left: 240px;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: 56px;
  padding: 0 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--surface);
  border-bottom: 1px solid var(--line);
  position: sticky;
  top: 0;
  z-index: 5;
}

.topbar-title {
  font-size: 17px;
  font-weight: 600;
}

.topbar-badge {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-weight: 500;
}

.panel {
  flex: 1;
  padding: 20px 28px 28px;
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 56px);
}

.chat-panel {
  padding-bottom: 0;
}

.chat-stream {
  flex: 1;
  overflow-y: auto;
  padding: 8px 4px 20px;
  max-height: calc(100vh - 56px - 120px);
}

.empty-chat {
  text-align: center;
  padding: 48px 24px;
  color: var(--muted);
}

.empty-chat h3 {
  margin: 0 0 8px;
  font-size: 18px;
  color: var(--text);
}

.empty-chat p {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  max-width: 400px;
  margin-left: auto;
  margin-right: auto;
}

.msg {
  display: flex;
  margin-bottom: 16px;
}

.msg.user {
  justify-content: flex-end;
}

.msg.assistant {
  justify-content: flex-start;
}

.msg-bubble {
  max-width: min(680px, 85%);
  padding: 12px 16px;
  border-radius: 14px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.msg.user .msg-bubble {
  background: var(--user-bubble);
  border: 1px solid #dbeafe;
  border-bottom-right-radius: 4px;
}

.msg.assistant .msg-bubble {
  background: var(--bot-bubble);
  border: 1px solid var(--line);
  border-bottom-left-radius: 4px;
}

.msg-text {
  font-size: 15px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg-time {
  margin-top: 8px;
  font-size: 11px;
  color: var(--muted);
}

.typing {
  display: flex;
  gap: 5px;
  align-items: center;
  padding: 16px 20px !important;
}

.typing span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #94a3b8;
  animation: bounce 1.2s infinite ease-in-out;
}

.typing span:nth-child(2) { animation-delay: 0.15s; }
.typing span:nth-child(3) { animation-delay: 0.3s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0.65); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

.composer {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  padding: 16px 0 24px;
  border-top: 1px solid var(--line);
  background: linear-gradient(180deg, transparent, #fff 30%);
  margin-top: auto;
}

.composer textarea {
  flex: 1;
  min-height: 48px;
  max-height: 140px;
  padding: 12px 16px;
  border: 1px solid var(--line);
  border-radius: 12px;
  font-size: 15px;
  font-family: inherit;
  resize: vertical;
  line-height: 1.5;
}

.composer textarea:focus {
  outline: none;
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.kb-panel {
  gap: 20px;
}

.upload-card {
  background: var(--surface);
  border-radius: var(--radius);
  border: 1px solid var(--line);
  padding: 22px 24px;
  box-shadow: var(--shadow);
}

.upload-head h3 {
  margin: 0 0 6px;
  font-size: 16px;
}

.upload-head p {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
}

.upload-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-top: 18px;
}

.file-pill {
  cursor: pointer;
}

.file-pill input {
  display: none;
}

.file-pill span {
  display: inline-block;
  padding: 10px 16px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 10px;
  font-size: 14px;
  color: var(--muted);
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cat-input {
  flex: 1;
  min-width: 180px;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 10px;
  font-size: 14px;
  font-family: inherit;
}

.table-card {
  background: var(--surface);
  border-radius: var(--radius);
  border: 1px solid var(--line);
  overflow: hidden;
  box-shadow: var(--shadow);
}

.table-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--line);
  font-weight: 600;
  font-size: 14px;
}

.table-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.err-cell {
  font-size: 12px;
  color: #b45309;
  max-width: 220px;
  word-break: break-all;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.data-table th,
.data-table td {
  padding: 14px 20px;
  text-align: left;
  border-bottom: 1px solid var(--line);
}

.data-table th {
  background: #f8fafc;
  font-weight: 600;
  color: var(--muted);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  background: #eff6ff;
  color: var(--primary);
  font-size: 12px;
}

.link.danger {
  background: none;
  border: none;
  color: #dc2626;
  cursor: pointer;
  font-size: 13px;
  font-family: inherit;
}

.link.danger:hover {
  text-decoration: underline;
}

.empty-cell {
  text-align: center;
  color: var(--muted);
  padding: 28px !important;
}

.empty-cell.flat {
  border: none;
}

.history-list {
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}

.history-item {
  padding: 16px 20px;
  border-bottom: 1px solid var(--line);
}

.history-item:last-child {
  border-bottom: none;
}

.history-q,
.history-a {
  font-size: 14px;
  line-height: 1.55;
  margin-bottom: 8px;
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.history-a {
  margin-bottom: 6px;
  color: var(--muted);
}

.badge {
  flex-shrink: 0;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
}

.badge.user {
  background: #dbeafe;
  color: var(--primary);
}

.badge.bot {
  background: #f1f5f9;
  color: var(--muted);
}

.history-meta {
  font-size: 11px;
  color: #94a3b8;
}

@media (max-width: 900px) {
  .sidebar {
    width: 72px;
    padding: 16px 8px;
  }

  .side-brand .brand-title,
  .side-brand .brand-sub,
  .nav-item span:not(.nav-ico),
  .side-user-meta,
  .btn-text {
    display: none;
  }

  .side-brand {
    justify-content: center;
  }

  .side-nav .nav-item {
    justify-content: center;
    padding: 12px;
  }

  .side-user {
    flex-direction: column;
    padding: 8px;
  }

  .main {
    margin-left: 72px;
  }
}
</style>
