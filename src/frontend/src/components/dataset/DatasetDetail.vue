<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { SavedDatasetDetailDTO, StandardResultDTO } from '@/types/contract'
import { getDataset, updateDatasetMeta } from '@/api/dataset'
import OutputRenderer from '@/components/output/OutputRenderer.vue'

const props = defineProps<{ datasetId: string }>()
const emit = defineEmits<{ (e: 'back'): void }>()

const detail = ref<SavedDatasetDetailDTO>()
const loading = ref(false)
const error = ref<string>()

const editingName = ref(false)
const nameInput = ref('')
const saveLoading = ref(false)
const saveError = ref<string>()

async function load() {
  loading.value = true
  error.value = undefined
  try {
    detail.value = await getDataset(props.datasetId, { pageSize: 500 })
    nameInput.value = detail.value.name
  }
  catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
  }
  finally {
    loading.value = false
  }
}

async function handleSaveName() {
  if (!nameInput.value.trim())
    return
  saveLoading.value = true
  saveError.value = undefined
  try {
    await updateDatasetMeta(props.datasetId, { name: nameInput.value.trim() })
    await load()
    editingName.value = false
  }
  catch (err) {
    saveError.value = err instanceof Error ? err.message : '保存失败'
  }
  finally {
    saveLoading.value = false
  }
}

const resultPayload = computed<StandardResultDTO | undefined>(() => {
  if (!detail.value)
    return undefined
  return {
    kind: 'TABLE',
    table: {
      title: detail.value.name,
      columns: detail.value.columns ?? [],
      rows: detail.value.rows ?? [],
      option: { pageable: true, pageSize: 20 },
      meta: { totalRows: detail.value.rowCount, returnedRows: detail.value.rows?.length },
    },
  }
})

onMounted(load)
</script>

<template>
  <section class="detail-page">
    <header class="detail-header">
      <button class="back-button" @click="emit('back')">← 返回列表</button>

      <!-- 可编辑名称 -->
      <div v-if="editingName" class="name-edit">
        <input v-model="nameInput" class="name-input" @keyup.enter="handleSaveName" @keyup.escape="editingName = false">
        <button class="primary-button" :disabled="saveLoading" @click="handleSaveName">
          {{ saveLoading ? '保存中...' : '保存' }}
        </button>
        <button class="ghost-button" @click="editingName = false">取消</button>
        <span v-if="saveError" class="inline-error">{{ saveError }}</span>
      </div>
      <div v-else class="name-view">
        <h2>{{ detail?.name ?? '加载中...' }}</h2>
        <button v-if="detail" class="ghost-button" @click="editingName = true; nameInput = detail?.name ?? ''">重命名</button>
      </div>

      <div class="header-actions">
        <span v-if="detail" class="row-count">{{ detail.rowCount?.toLocaleString() ?? '—' }} 行</span>
      </div>
    </header>

    <div class="detail-body">
      <div v-if="loading" class="page-state">加载中...</div>
      <div v-else-if="error" class="page-state page-state--error">{{ error }}</div>
      <OutputRenderer v-else :result="resultPayload" mode="runtime" />
    </div>
  </section>
</template>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 72px);
  padding: 24px;
  gap: 16px;
}
.detail-header {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.back-button {
  background: none;
  border: none;
  cursor: pointer;
  color: #64748b;
  font-size: 13px;
  padding: 6px 0;
  white-space: nowrap;
}
.back-button:hover {
  color: #94a3b8;
}
.name-view,
.name-edit {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
}
.name-view h2 {
  margin: 0;
  font-size: 20px;
  color: #e2e8f0;
}
.name-input {
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 8px;
  padding: 6px 10px;
  color: #e2e8f0;
  font-size: 14px;
  min-width: 200px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}
.row-count {
  color: #64748b;
  font-size: 12px;
  font-family: 'JetBrains Mono', ui-monospace, monospace;
}
.export-error {
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(127, 29, 29, 0.28);
  border: 1px solid rgba(248, 113, 113, 0.32);
  color: #fecaca;
  font-size: 13px;
}
.detail-body {
  flex: 1;
}
.page-state {
  border: 1px dashed #334155;
  border-radius: 16px;
  padding: 32px;
  color: #94a3b8;
  text-align: center;
}
.page-state--error {
  color: #fecaca;
  border-color: rgba(248, 113, 113, 0.35);
  background: rgba(127, 29, 29, 0.2);
}
.primary-button,
.ghost-button {
  border-radius: 8px;
  padding: 6px 12px;
  cursor: pointer;
  font-size: 12px;
}
.primary-button {
  border: none;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: #fff;
}
.primary-button:disabled {
  opacity: 0.5;
  cursor: default;
}
.ghost-button {
  border: 1px solid #334155;
  background: transparent;
  color: #cbd5e1;
}
.ghost-button:disabled {
  opacity: 0.45;
  cursor: default;
}
.inline-error {
  color: #fca5a5;
  font-size: 11px;
}
</style>
