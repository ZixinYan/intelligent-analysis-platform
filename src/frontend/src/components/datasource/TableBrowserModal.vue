<script setup lang="ts">
import { ref, watch } from 'vue'
import { getDatasourceTables } from '@/api/datasource'

const props = defineProps<{
  visible: boolean
  datasourceId: string
  datasourceName: string
}>()

const tables = ref<string[]>([])
const loading = ref(false)
const error = ref<string>()
const search = ref('')

async function load() {
  loading.value = true
  error.value = undefined
  tables.value = []
  try {
    tables.value = await getDatasourceTables(props.datasourceId)
  }
  catch (err) {
    error.value = err instanceof Error ? err.message : '加载表结构失败'
  }
  finally {
    loading.value = false
  }
}

watch(() => props.visible, (visible) => {
  if (visible) {
    search.value = ''
    load()
  }
})

const filteredTables = () =>
  search.value.trim()
    ? tables.value.filter(t => t.toLowerCase().includes(search.value.toLowerCase()))
    : tables.value
</script>

<template>
  <div v-if="visible" class="dialog-mask" @click.self="$emit('close')">
    <div class="dialog">
      <div class="dialog__header">
        <div>
          <h3>表结构浏览</h3>
          <p>{{ datasourceName }}</p>
        </div>
        <button type="button" class="ghost-button" @click="$emit('close')">关闭</button>
      </div>

      <div class="dialog__body">
        <input
          v-model="search"
          class="search-input"
          placeholder="搜索表名..."
        />

        <div v-if="loading" class="state-msg">加载中...</div>
        <div v-else-if="error" class="state-msg state-msg--error">{{ error }}</div>
        <div v-else-if="!tables.length" class="state-msg">该数据源暂无可见表</div>
        <ul v-else class="table-list">
          <li
            v-for="table in filteredTables()"
            :key="table"
            class="table-list__item"
          >
            <span class="table-icon">⊞</span>
            {{ table }}
          </li>
          <li v-if="!filteredTables().length" class="state-msg state-msg--inner">无匹配结果</li>
        </ul>
      </div>

      <div class="dialog__footer">
        <span class="table-count">共 {{ tables.length }} 张表</span>
        <button type="button" class="ghost-button" @click="$emit('close')">关闭</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialog-mask {
  position: fixed;
  inset: 0;
  background: color-mix(in srgb, var(--iap-body-bg) 35%, rgba(15, 23, 42, 0.72));
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  z-index: 20;
}
.dialog {
  width: min(520px, 100%);
  border: 1px solid var(--iap-divider);
  border-radius: 20px;
  background: var(--iap-panel-bg);
  box-shadow: var(--iap-shadow-panel);
  display: flex;
  flex-direction: column;
  max-height: 80vh;
}
.dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-bottom: 1px solid var(--iap-divider);
  flex-shrink: 0;
}
.dialog__header h3 {
  margin: 0;
  font-size: 20px;
  color: var(--iap-text-primary);
}
.dialog__header p {
  margin: 4px 0 0;
  color: var(--iap-text-tertiary);
  font-size: 14px;
}
.dialog__body {
  padding: 20px 24px;
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.dialog__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-top: 1px solid var(--iap-divider);
  flex-shrink: 0;
}
.search-input {
  width: 100%;
  border: 1px solid var(--iap-input-border);
  border-radius: 12px;
  background: var(--iap-input-bg);
  color: var(--iap-text-primary);
  padding: 10px 12px;
  box-sizing: border-box;
  outline: none;
}
.search-input:focus {
  border-color: var(--iap-input-border-focus);
  box-shadow: 0 0 0 3px var(--iap-accent-ring);
}
.table-list {
  margin: 0;
  padding: 0;
  list-style: none;
  overflow-y: auto;
  flex: 1;
}
.table-list__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  color: var(--iap-text-secondary);
  font-size: 14px;
  font-family: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace;
  transition: background 0.1s, color 0.1s;
}
.table-list__item:hover {
  background: color-mix(in srgb, var(--iap-text-accent) 12%, var(--iap-surface-hover));
  color: var(--iap-text-accent);
}
.table-icon {
  color: var(--iap-text-placeholder);
  font-size: 12px;
}
.state-msg {
  padding: 24px;
  text-align: center;
  color: var(--iap-text-tertiary);
  font-size: 14px;
}
.state-msg--error {
  color: var(--iap-error-text);
}
.state-msg--inner {
  list-style: none;
}
.table-count {
  color: var(--iap-text-tertiary);
  font-size: 13px;
}
.ghost-button {
  border: 1px solid var(--iap-btn-secondary-border);
  border-radius: 12px;
  background: var(--iap-btn-secondary-bg);
  color: var(--iap-btn-secondary-text);
  padding: 10px 16px;
  cursor: pointer;
}
.ghost-button:hover {
  background: var(--iap-btn-secondary-hover);
  color: var(--iap-btn-secondary-text-strong);
}
</style>
