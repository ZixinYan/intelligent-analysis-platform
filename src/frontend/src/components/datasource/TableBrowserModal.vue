<script setup lang="ts">
import { ref, watch } from 'vue'
import { getDatasourceTables, getTableSchema } from '@/api/datasource'
import type { FieldSchemaDTO } from '@/types/contract'

const props = defineProps<{
  visible: boolean
  datasourceId: string
  datasourceName: string
}>()

const tables = ref<string[]>([])
const loading = ref(false)
const error = ref<string>()
const search = ref('')

// 每张表的字段信息：tableName → fields
const tableFields = ref<Record<string, FieldSchemaDTO[]>>({})
// 正在加载字段的表
const loadingFields = ref<Record<string, boolean>>({})
// 已展开的表
const expandedTables = ref<Set<string>>(new Set())

async function load() {
  loading.value = true
  error.value = undefined
  tables.value = []
  tableFields.value = {}
  expandedTables.value = new Set()
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

async function toggleTable(tableName: string) {
  if (expandedTables.value.has(tableName)) {
    expandedTables.value.delete(tableName)
    return
  }
  expandedTables.value.add(tableName)
  if (tableFields.value[tableName] !== undefined) return
  loadingFields.value[tableName] = true
  try {
    tableFields.value[tableName] = await getTableSchema(props.datasourceId, tableName)
  }
  catch {
    tableFields.value[tableName] = []
  }
  finally {
    delete loadingFields.value[tableName]
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
          <template v-for="table in filteredTables()" :key="table">
            <li class="table-list__item" @click="toggleTable(table)">
              <span class="table-icon">⊞</span>
              <span class="table-name">{{ table }}</span>
              <span class="table-expand-icon" :class="{ 'table-expand-icon--open': expandedTables.has(table) }">▶</span>
            </li>
            <li v-if="expandedTables.has(table)" class="table-fields">
              <div v-if="loadingFields[table]" class="table-fields__loading">加载字段中…</div>
              <template v-else-if="tableFields[table]?.length">
                <div v-for="field in tableFields[table]" :key="field.name ?? field.fieldId" class="table-fields__row">
                  <span class="table-fields__name">{{ field.name }}</span>
                  <span class="table-fields__type">{{ field.valueType }}</span>
                </div>
              </template>
              <div v-else class="table-fields__loading">暂无字段信息</div>
            </li>
          </template>
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
  cursor: pointer;
}
.table-list__item:hover {
  background: color-mix(in srgb, var(--iap-text-accent) 12%, var(--iap-surface-hover));
  color: var(--iap-text-accent);
}
.table-icon {
  color: var(--iap-text-placeholder);
  font-size: 12px;
  flex-shrink: 0;
}
.table-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.table-expand-icon {
  font-size: 9px;
  color: var(--iap-text-placeholder);
  transition: transform 0.15s;
  flex-shrink: 0;
}
.table-expand-icon--open {
  transform: rotate(90deg);
}
.table-fields {
  list-style: none;
  padding: 0 12px 6px 34px;
  border-bottom: 1px solid var(--iap-divider);
  margin-bottom: 2px;
}
.table-fields__loading {
  font-size: 11px;
  color: var(--iap-text-tertiary);
  padding: 4px 0;
}
.table-fields__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 3px 0;
}
.table-fields__name {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 12px;
  color: var(--iap-text-secondary);
}
.table-fields__type {
  font-size: 11px;
  color: var(--iap-text-tertiary);
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  flex-shrink: 0;
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
