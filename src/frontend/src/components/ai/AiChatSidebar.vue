<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { streamChat } from '@/api/ai'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
}

const open = ref(false)
const messages = ref<Message[]>([])
const inputText = ref('')
const sending = ref(false)
const messagesEl = ref<HTMLElement>()

let abortController: AbortController | null = null

function scrollToBottom() {
  nextTick(() => {
    if (messagesEl.value) {
      messagesEl.value.scrollTop = messagesEl.value.scrollHeight
    }
  })
}

async function send() {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  inputText.value = ''
  const userId = Date.now().toString()
  messages.value.push({ id: userId, role: 'user', content: text })
  scrollToBottom()

  const assistantId = (Date.now() + 1).toString()
  messages.value.push({ id: assistantId, role: 'assistant', content: '' })
  sending.value = true

  abortController = new AbortController()
  try {
    for await (const token of streamChat(text, abortController.signal)) {
      const msg = messages.value.find(m => m.id === assistantId)
      if (msg) {
        msg.content += token
        scrollToBottom()
      }
    }
  }
  catch (err) {
    if ((err as Error).name !== 'AbortError') {
      const msg = messages.value.find(m => m.id === assistantId)
      if (msg) msg.content = '请求失败，请重试。'
    }
  }
  finally {
    sending.value = false
    abortController = null
    scrollToBottom()
  }
}

function stop() {
  abortController?.abort()
}

function clear() {
  messages.value = []
}

function handleKeyDown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}
</script>

<template>
  <!-- 悬浮触发按钮 -->
  <button
    class="ai-chat-fab"
    :class="{ 'ai-chat-fab--active': open }"
    title="AI 助手"
    @click="open = !open"
  >
    <span class="ai-chat-fab__icon">✦</span>
  </button>

  <!-- 侧边栏 -->
  <transition name="ai-sidebar-slide">
    <div v-if="open" class="ai-chat-sidebar">
      <!-- 头部 -->
      <header class="ai-chat-sidebar__header">
        <span class="ai-chat-sidebar__title">✦ AI 助手</span>
        <div class="ai-chat-sidebar__actions">
          <button class="ai-chat-sidebar__btn" @click="clear">清空</button>
          <button class="ai-chat-sidebar__btn" @click="open = false">✕</button>
        </div>
      </header>

      <!-- 消息列表 -->
      <div ref="messagesEl" class="ai-chat-sidebar__messages">
        <div v-if="messages.length === 0" class="ai-chat-sidebar__empty">
          你好！我可以帮你生成 SQL、推荐图表类型或构建工作流。
        </div>
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="ai-chat-sidebar__msg"
          :class="`ai-chat-sidebar__msg--${msg.role}`"
        >
          <div class="ai-chat-sidebar__bubble">
            <pre class="ai-chat-sidebar__text">{{ msg.content || '▌' }}</pre>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="ai-chat-sidebar__input-area">
        <textarea
          v-model="inputText"
          class="ai-chat-sidebar__textarea"
          rows="2"
          placeholder="输入问题，Enter 发送，Shift+Enter 换行"
          :disabled="sending"
          @keydown="handleKeyDown"
        />
        <button
          v-if="sending"
          class="ai-chat-sidebar__send-btn ai-chat-sidebar__send-btn--stop"
          @click="stop"
        >
          停止
        </button>
        <button
          v-else
          class="ai-chat-sidebar__send-btn"
          :disabled="!inputText.trim()"
          @click="send"
        >
          发送
        </button>
      </div>
    </div>
  </transition>
</template>

<style scoped>
/* ── 悬浮按钮 ─────────────────────────────────── */
.ai-chat-fab {
  position: fixed;
  right: 20px;
  bottom: 24px;
  z-index: 40;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: 1px solid rgba(99, 102, 241, 0.5);
  background: rgba(99, 102, 241, 0.2);
  color: #a5b4fc;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
  transition: background 0.15s, transform 0.15s;
}
.ai-chat-fab:hover { background: rgba(99, 102, 241, 0.35); transform: scale(1.08); }
.ai-chat-fab--active { background: rgba(99, 102, 241, 0.4); }
.ai-chat-fab__icon { line-height: 1; }

/* ── 侧边栏 ───────────────────────────────────── */
.ai-chat-sidebar {
  position: fixed;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 30;
  width: 320px;
  display: flex;
  flex-direction: column;
  border-left: 1px solid #1e293b;
  background: #0a0f1e;
  box-shadow: -8px 0 32px rgba(0, 0, 0, 0.4);
}

/* ── 头部 ─────────────────────────────────────── */
.ai-chat-sidebar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #1e293b;
  flex-shrink: 0;
}
.ai-chat-sidebar__title {
  font-size: 13px;
  font-weight: 600;
  color: #a5b4fc;
}
.ai-chat-sidebar__actions { display: flex; gap: 4px; }
.ai-chat-sidebar__btn {
  background: none;
  border: 1px solid #334155;
  border-radius: 6px;
  color: #64748b;
  font-size: 11px;
  padding: 3px 8px;
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
}
.ai-chat-sidebar__btn:hover { color: #e2e8f0; border-color: #64748b; }

/* ── 消息列表 ─────────────────────────────────── */
.ai-chat-sidebar__messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ai-chat-sidebar__empty {
  text-align: center;
  font-size: 12px;
  color: #475569;
  margin-top: 32px;
  line-height: 1.5;
}
.ai-chat-sidebar__msg {
  display: flex;
}
.ai-chat-sidebar__msg--user { justify-content: flex-end; }
.ai-chat-sidebar__msg--assistant { justify-content: flex-start; }
.ai-chat-sidebar__bubble {
  max-width: 82%;
  border-radius: 12px;
  padding: 8px 12px;
}
.ai-chat-sidebar__msg--user .ai-chat-sidebar__bubble {
  background: rgba(99, 102, 241, 0.25);
  border: 1px solid rgba(99, 102, 241, 0.4);
}
.ai-chat-sidebar__msg--assistant .ai-chat-sidebar__bubble {
  background: rgba(15, 23, 42, 0.8);
  border: 1px solid #1e293b;
}
.ai-chat-sidebar__text {
  font-family: inherit;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  color: #cbd5e1;
}
.ai-chat-sidebar__msg--user .ai-chat-sidebar__text { color: #e2e8f0; }

/* ── 输入区 ───────────────────────────────────── */
.ai-chat-sidebar__input-area {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #1e293b;
  flex-shrink: 0;
}
.ai-chat-sidebar__textarea {
  flex: 1;
  resize: none;
  background: #020617;
  border: 1px solid #334155;
  border-radius: 8px;
  color: #e2e8f0;
  font-size: 12px;
  font-family: inherit;
  padding: 8px 10px;
  line-height: 1.5;
  outline: none;
  transition: border-color 0.15s;
}
.ai-chat-sidebar__textarea:focus { border-color: rgba(99, 102, 241, 0.6); }
.ai-chat-sidebar__textarea:disabled { opacity: 0.6; }
.ai-chat-sidebar__send-btn {
  flex-shrink: 0;
  padding: 7px 12px;
  border-radius: 8px;
  font-size: 12px;
  cursor: pointer;
  border: 1px solid rgba(99, 102, 241, 0.5);
  background: rgba(99, 102, 241, 0.2);
  color: #a5b4fc;
  transition: background 0.15s;
}
.ai-chat-sidebar__send-btn:hover:not(:disabled) { background: rgba(99, 102, 241, 0.35); }
.ai-chat-sidebar__send-btn:disabled { opacity: 0.45; cursor: not-allowed; }
.ai-chat-sidebar__send-btn--stop {
  border-color: rgba(239, 68, 68, 0.5);
  background: rgba(239, 68, 68, 0.12);
  color: #fca5a5;
}
.ai-chat-sidebar__send-btn--stop:hover { background: rgba(239, 68, 68, 0.25); }

/* ── 滑入动画 ─────────────────────────────────── */
.ai-sidebar-slide-enter-active,
.ai-sidebar-slide-leave-active {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.ai-sidebar-slide-enter-from,
.ai-sidebar-slide-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
</style>
