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
const singleValue = computed(() => String(props.modelValue ?? ''))
const isPlaceholderVisible = computed(() => !singleValue.value)

onMounted(() => {
  store.load().catch(() => undefined)
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
        :class="{ 'ds-select__input--placeholder': isPlaceholderVisible }"
        :value="singleValue"
        :disabled="disabled || store.loading"
        @change="handleChange"
      >
        <option value="" disabled hidden>
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
  gap: 10px;
}

.ds-select__container {
  position: relative;
}

.ds-select__input {
  width: 100%;
  min-height: 44px;
  border: 1px solid var(--iap-input-border);
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, var(--iap-input-bg-focus) 100%);
  color: var(--iap-text-primary);
  padding: 11px 44px 11px 14px;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 14 14'%3E%3Cpath fill='%2398a2b2' d='M7 9.25 2.5 4.75h9z'/%3E%3C/svg%3E"), linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, var(--iap-input-bg-focus) 100%);
  background-repeat: no-repeat, no-repeat;
  background-position: right 14px center, center;
  background-size: 14px 14px, auto;
  box-shadow: var(--iap-select-shadow);
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, background 0.15s ease, transform 0.15s ease;
  font-size: 13px;
  outline: none;
}

.ds-select__input:hover:not(:disabled) {
  border-color: var(--iap-divider-strong);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 14 14'%3E%3Cpath fill='%2398a2b2' d='M7 9.25 2.5 4.75h9z'/%3E%3C/svg%3E"), linear-gradient(180deg, #ffffff 0%, var(--iap-input-bg-hover) 100%);
  transform: translateY(-1px);
}

.ds-select__input:focus {
  border-color: var(--iap-input-border-focus);
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='14' height='14' viewBox='0 0 14 14'%3E%3Cpath fill='%23155aef' d='M7 9.25 2.5 4.75h9z'/%3E%3C/svg%3E"), linear-gradient(180deg, #ffffff 0%, var(--iap-input-bg-focus) 100%);
  box-shadow: var(--iap-select-shadow), var(--iap-select-shadow-focus);
}

.ds-select__input:disabled {
  opacity: 0.62;
  cursor: not-allowed;
  transform: none;
}

.ds-select__input--placeholder {
  color: var(--iap-text-placeholder);
}

.is-loading .ds-select__input {
  background-image: linear-gradient(180deg, rgba(255, 255, 255, 0.96) 0%, var(--iap-input-bg-focus) 100%);
  padding-right: 44px;
}

.ds-select__spinner {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
  width: 16px;
  height: 16px;
  border: 2px solid var(--iap-divider-strong);
  border-top-color: var(--iap-text-accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  pointer-events: none;
}

@keyframes spin {
  to { transform: translateY(-50%) rotate(360deg); }
}

.ds-select__card {
  padding: 12px 14px;
  border: 1px solid var(--iap-card-border);
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98) 0%, var(--iap-card-bg) 100%);
  display: grid;
  gap: 6px;
  box-shadow: var(--iap-shadow-panel);
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
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.ds-select__status--active { background: #17b26a; box-shadow: 0 0 0 4px rgba(23, 178, 106, 0.12); }
.ds-select__status--inactive { background: var(--iap-text-disabled); }
.ds-select__status--unreachable { background: #f04438; box-shadow: 0 0 0 4px rgba(240, 68, 56, 0.12); }

.ds-select__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--iap-text-primary);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ds-select__type-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 999px;
  letter-spacing: 0.04em;
  background: rgba(21, 90, 239, 0.08);
  border: 1px solid rgba(21, 90, 239, 0.14);
  color: var(--iap-text-accent);
  white-space: nowrap;
  flex-shrink: 0;
}

.ds-select__meta {
  font-size: 11px;
  color: var(--iap-text-tertiary);
  font-family: 'SFMono-Regular', ui-monospace, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ds-select__error {
  font-size: 12px;
  color: var(--iap-error-text);
}
</style>
