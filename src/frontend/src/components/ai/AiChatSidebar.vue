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
  <button
    class="ai-chat-fab"
    :class="{ 'ai-chat-fab--active': open }"
    title="AI 助手"
    @click="open = !open"
  >
    <span class="ai-chat-fab__icon">✦</span>
  </button>

  <transition name="ai-sidebar-slide">
    <div v-if="open" class="ai-chat-sidebar">
      <header class="ai-chat-sidebar__header">
        <span class="ai-chat-sidebar__title">✦ AI 助手</span>
        <div class="ai-chat-sidebar__actions">
          <button class="ai-chat-sidebar__btn" @click="clear">清空</button>
          <button class="ai-chat-sidebar__btn" @click="open = false">✕</button>
        </div>
      </header>

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
.ai-chat-fab {
  position: fixed;
  right: 20px;
  bottom: 24px;
  z-index: 40;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  border: 1px solid var(--iap-ai-btn-border);
  background: var(--iap-ai-btn-bg);
  color: var(--iap-ai-btn-text);
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: var(--iap-shadow-panel);
  transition: background 0.15s, transform 0.15s;
}
.ai-chat-fab:hover { background: var(--iap-ai-btn-hover); transform: scale(1.08); }
.ai-chat-fab--active { background: var(--iap-ai-btn-hover); }
.ai-chat-fab__icon { line-height: 1; }

.ai-chat-sidebar {
  position: fixed;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 30;
  width: 320px;
  display: flex;
  flex-direction: column;
  border-left: 1px solid var(--iap-divider);
  background: var(--iap-panel-bg);
  box-shadow: -8px 0 32px rgba(0, 0, 0, 0.18);
}

.ai-chat-sidebar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--iap-divider);
  flex-shrink: 0;
}
.ai-chat-sidebar__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--iap-ai-btn-text);
}
.ai-chat-sidebar__actions { display: flex; gap: 4px; }
.ai-chat-sidebar__btn {
  background: var(--iap-btn-secondary-bg);
  border: 1px solid var(--iap-btn-secondary-border);
  border-radius: 6px;
  color: var(--iap-text-tertiary);
  font-size: 11px;
  padding: 3px 8px;
  cursor: pointer;
}
.ai-chat-sidebar__btn:hover { color: var(--iap-text-primary); border-color: var(--iap-divider-strong); }

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
  color: var(--iap-text-tertiary);
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
  background: var(--iap-user-bubble-bg);
  border: 1px solid var(--iap-user-bubble-border);
}
.ai-chat-sidebar__msg--assistant .ai-chat-sidebar__bubble {
  background: var(--iap-assistant-bubble-bg);
  border: 1px solid var(--iap-assistant-bubble-border);
}
.ai-chat-sidebar__text {
  font-family: inherit;
  font-size: 12px;
  line-height: 1.55;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  color: var(--iap-text-primary);
}

.ai-chat-sidebar__input-area {
  padding: 12px;
  border-top: 1px solid var(--iap-divider);
  display: grid;
  gap: 10px;
}
.ai-chat-sidebar__textarea {
  width: 100%;
  resize: none;
  border: 1px solid var(--iap-input-border);
  border-radius: 12px;
  background: var(--iap-input-bg);
  color: var(--iap-text-primary);
  padding: 10px 12px;
  outline: none;
}
.ai-chat-sidebar__textarea:focus {
  border-color: var(--iap-input-border-focus);
  box-shadow: 0 0 0 3px var(--iap-accent-ring);
}
.ai-chat-sidebar__send-btn {
  justify-self: end;
  border: none;
  border-radius: 10px;
  background: var(--iap-btn-primary-bg);
  color: var(--iap-btn-primary-text);
  padding: 8px 16px;
  cursor: pointer;
}
.ai-chat-sidebar__send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.ai-chat-sidebar__send-btn--stop {
  background: var(--iap-btn-danger-bg);
  border: 1px solid var(--iap-btn-danger-border);
  color: var(--iap-btn-danger-text);
}
</style>
