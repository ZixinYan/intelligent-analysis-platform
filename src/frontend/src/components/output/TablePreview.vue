<script setup lang="ts">
import { computed } from 'vue'
import type { QueryResultDTO, StandardResultDTO } from '@/types/contract'
import { resolveRendererModel, type RendererMode } from '@/components/output/renderer'

const props = defineProps<{
  result?: StandardResultDTO | QueryResultDTO
  mode?: RendererMode
}>()

const model = computed(() => resolveRendererModel(props.result, props.mode ?? 'runtime'))
const fallback = computed(() => model.value.fallback ?? { title: '暂无表格数据', description: '当前结果暂不可展示。' })

function formatValue(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value)
    }
    catch {
      return String(value)
    }
  }
  return String(value)
}
</script>

<template>
  <div class="table-preview">
    <div class="table-preview__header">
      <strong>{{ model.title }}</strong>
      <span>{{ model.kind === 'table' ? `${model.rows.length} 行` : '空结果' }}</span>
    </div>
    <div v-for="notice in model.notices" :key="notice.message" class="table-preview__notice" :data-tone="notice.tone">
      {{ notice.message }}
    </div>
    <template v-if="model.kind === 'table' && !model.empty">
      <div class="table-preview__scroll">
        <table>
          <thead>
            <tr>
              <th v-for="column in model.columns" :key="column.key">{{ column.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in model.rows" :key="index">
              <td v-for="column in model.columns" :key="column.key">{{ formatValue(row[column.key]) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="table-preview__meta">
        <span v-if="model.pageable">分页 {{ model.pageSize }} / 页</span>
        <span v-if="model.downloadable">支持下载</span>
      </div>
    </template>
    <div v-else class="table-preview__empty">
      <strong>{{ fallback.title }}</strong>
      <span>{{ fallback.description }}</span>
    </div>
  </div>
</template>

<style scoped>
.table-preview {
  display: grid;
  gap: 10px;
  overflow: hidden;
  border: 1px solid #1e293b;
  border-radius: 16px;
  background: #0b1120;
  padding: 14px;
}
.table-preview__header,
.table-preview__meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: #94a3b8;
  font-size: 12px;
}
.table-preview__header strong {
  color: #e2e8f0;
  font-size: 14px;
}
.table-preview__notice {
  padding: 8px 10px;
  border-radius: 10px;
  font-size: 12px;
  color: #cbd5e1;
  background: rgba(59, 130, 246, 0.12);
}
.table-preview__notice[data-tone='warning'] {
  background: rgba(245, 158, 11, 0.14);
}
.table-preview__scroll {
  overflow: auto;
}
.table-preview table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.table-preview th,
.table-preview td {
  border: 1px solid #334155;
  padding: 8px 10px;
  text-align: left;
  vertical-align: top;
}
.table-preview th {
  color: #cbd5e1;
  background: rgba(15, 23, 42, 0.95);
}
.table-preview td {
  color: #e2e8f0;
}
.table-preview__empty {
  display: grid;
  gap: 4px;
  padding: 18px;
  border: 1px dashed #334155;
  border-radius: 12px;
  color: #94a3b8;
  text-align: center;
}
.table-preview__empty strong {
  color: #e2e8f0;
}
</style>
