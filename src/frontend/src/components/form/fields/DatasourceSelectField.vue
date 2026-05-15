<script setup lang="ts">
import { computed, onMounted } from 'vue'
import type { PanelFieldDTO } from '@/types/contract'
import { useDatasourceStore } from '@/stores/datasource'

const props = defineProps<{
  field: PanelFieldDTO
  modelValue: unknown
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const store = useDatasourceStore()

onMounted(() => {
  store.load()
})

const selected = computed(() => store.datasources.find(ds => ds.id === props.modelValue))

function handleChange(event: Event) {
  emit('update:modelValue', (event.target as HTMLSelectElement).value)
}
</script>

<template>
  <div class="ds-select">
    <div class="ds-select__container" :class="{ 'is-loading': store.loading }">
      <select
        class="ds-select__input"
        :value="String(modelValue ?? '')"
        :disabled="disabled || store.loading"
        @change="handleChange"
      >
        <option value="" disabled>
          {{ store.loading ? '加载中...' : (field.placeholder ?? '请选择数据源') }}
        </option>
        <option
          v-for="ds in store.datasources"
          :key="ds.id"
          :value="ds.id"
        >
          {{ ds.name }} ({{ ds.type }})
        </option>
      </select>
      <span v-if="store.loading" class="ds-select__spinner" aria-hidden="true" />
    </div>

    <!-- 选中数据源的详情卡片 -->
    <div v-if="selected" class="ds-select__card">
      <div class="ds-select__card-row">
        <span
          class="ds-select__status"
          :class="`ds-select__status--${(selected.status ?? 'INACTIVE').toLowerCase()}`"
        />
        <span class="ds-select__name">{{ selected.name }}</span>
        <span class="ds-select__type-badge">{{ selected.type }}</span>
      </div>
      <div class="ds-select__card-row ds-select__card-row--meta">
        <span class="ds-select__meta">{{ selected.host }}:{{ selected.port }} / {{ selected.database }}</span>
      </div>
    </div>

    <div v-if="store.error" class="ds-select__error">{{ store.error }}</div>
  </div>
</template>

<style scoped>
.ds-select {
  display: grid;
  gap: 8px;
}

.ds-select__container {
  position: relative;
}

.ds-select__input {
  width: 100%;
  border: 1px solid #334155;
  border-radius: 12px;
  background: #020617;
  color: inherit;
  padding: 10px 36px 10px 12px;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%2394a3b8' d='M6 8L1 3h10z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 12px center;
  cursor: pointer;
  transition: border-color 0.15s;
  font-size: 13px;
}

.ds-select__input:hover:not(:disabled) {
  border-color: #475569;
}

.ds-select__input:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
}

.ds-select__input:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.is-loading .ds-select__input {
  background-image: none;
  padding-right: 40px;
}

.ds-select__spinner {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  width: 14px;
  height: 14px;
  border: 2px solid #334155;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  pointer-events: none;
}

@keyframes spin {
  to { transform: translateY(-50%) rotate(360deg); }
}

/* 选中详情卡 */
.ds-select__card {
  padding: 10px 12px;
  border: 1px solid #1e293b;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.8);
  display: grid;
  gap: 5px;
}

.ds-select__card-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ds-select__card-row--meta {
  padding-left: 18px;
}

.ds-select__status {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

.ds-select__status--active   { background: #22c55e; box-shadow: 0 0 5px rgba(34,197,94,0.5); }
.ds-select__status--inactive { background: #475569; }
.ds-select__status--unreachable { background: #ef4444; box-shadow: 0 0 5px rgba(239,68,68,0.5); }

.ds-select__name {
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ds-select__type-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 7px;
  border-radius: 4px;
  letter-spacing: 0.05em;
  background: rgba(56, 189, 248, 0.1);
  border: 1px solid rgba(56, 189, 248, 0.25);
  color: #38bdf8;
  white-space: nowrap;
  flex-shrink: 0;
}

.ds-select__meta {
  font-size: 11px;
  color: #475569;
  font-family: 'SFMono-Regular', ui-monospace, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ds-select__error {
  font-size: 12px;
  color: #fca5a5;
}
</style>
