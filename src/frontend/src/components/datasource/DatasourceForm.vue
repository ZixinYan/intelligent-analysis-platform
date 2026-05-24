<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { DatasourceCreateRequestDTO, DatasourceDTO, DatasourceType, DatasourceUpdateRequestDTO } from '@/types/contract'
import { getDatasource } from '@/api/datasource'
import { useDatasourceStore } from '@/stores/datasource'

const props = defineProps<{
  visible: boolean
  editingId?: string
}>()

const emit = defineEmits<{
  close: []
  saved: []
}>()

const store = useDatasourceStore()

const form = reactive({
  name: '',
  type: 'MYSQL' as DatasourceType,
  host: '',
  port: 3306,
  database: '',
  username: '',
  password: '',
  readonly: true,
  jdbcOptions: '',
})

const resetPassword = ref(false)
const loading = ref(false)
const submitError = ref<string>()
const errors = reactive<Record<string, string | undefined>>({})

const isEdit = computed(() => Boolean(props.editingId))
const title = computed(() => isEdit.value ? '编辑数据源' : '新建数据源')

function resetForm() {
  form.name = ''
  form.type = 'MYSQL'
  form.host = ''
  form.port = 3306
  form.database = ''
  form.username = ''
  form.password = ''
  form.readonly = true
  form.jdbcOptions = ''
  resetPassword.value = false
  submitError.value = undefined
  Object.keys(errors).forEach((key) => {
    errors[key] = undefined
  })
}

function applyDatasource(datasource: DatasourceDTO) {
  form.name = datasource.name ?? datasource.datasourceName ?? ''
  form.type = ((datasource.type ?? datasource.datasourceType ?? 'MYSQL') as DatasourceType)
  form.host = datasource.host ?? ''
  form.port = datasource.port ?? 0
  form.database = datasource.database ?? ''
  form.username = datasource.username ?? ''
  form.password = ''
  form.readonly = Boolean(datasource.readonly)
  form.jdbcOptions = datasource.jdbcOptions ? JSON.stringify(datasource.jdbcOptions, null, 2) : ''
}

function parseJdbcOptions() {
  if (!form.jdbcOptions.trim()) {
    return undefined
  }
  try {
    return JSON.parse(form.jdbcOptions) as Record<string, string>
  }
  catch {
    errors.jdbcOptions = 'JDBC 参数需为 JSON 对象'
    return null
  }
}

function validate() {
  errors.name = form.name.trim() ? undefined : '请输入名称'
  errors.host = form.host.trim() ? undefined : '请输入主机'
  errors.port = form.port > 0 ? undefined : '端口需大于 0'
  errors.database = form.database.trim() ? undefined : '请输入数据库名'
  errors.username = form.username.trim() ? undefined : '请输入用户名'
  errors.password = !isEdit.value || resetPassword.value
    ? form.password.trim() ? undefined : '请输入密码'
    : undefined
  const jdbcOptions = parseJdbcOptions()
  if (jdbcOptions === null) {
    return undefined
  }
  const valid = Object.values(errors).every(item => !item)
  return valid ? jdbcOptions : undefined
}

async function loadDatasource(id: string) {
  loading.value = true
  submitError.value = undefined
  try {
    applyDatasource(await getDatasource(id))
  }
  catch (err) {
    submitError.value = err instanceof Error ? err.message : '加载数据源失败'
  }
  finally {
    loading.value = false
  }
}

watch(() => props.visible, (visible) => {
  if (!visible) {
    resetForm()
    return
  }
  if (props.editingId) {
    loadDatasource(props.editingId)
    return
  }
  resetForm()
}, { immediate: true })

watch(() => props.editingId, (editingId) => {
  if (!props.visible) {
    return
  }
  if (editingId) {
    loadDatasource(editingId)
    return
  }
  resetForm()
})

async function handleSubmit() {
  submitError.value = undefined
  const jdbcOptions = validate()
  if (jdbcOptions === undefined && Object.values(errors).some(item => item)) {
    return
  }
  loading.value = true
  try {
    if (isEdit.value && props.editingId) {
      const payload: DatasourceUpdateRequestDTO = {
        name: form.name.trim(),
        type: form.type,
        host: form.host.trim(),
        port: form.port,
        database: form.database.trim(),
        username: form.username.trim(),
        readonly: form.readonly,
        jdbcOptions: jdbcOptions ?? undefined,
      }
      if (resetPassword.value) {
        payload.password = form.password
      }
      await store.update(props.editingId, payload)
    }
    else {
      const payload: DatasourceCreateRequestDTO = {
        name: form.name.trim(),
        type: form.type,
        host: form.host.trim(),
        port: form.port,
        database: form.database.trim(),
        username: form.username.trim(),
        password: form.password,
        readonly: form.readonly,
        jdbcOptions: jdbcOptions ?? undefined,
      }
      await store.create(payload)
    }
    emit('saved')
    emit('close')
  }
  catch (err) {
    submitError.value = err instanceof Error ? err.message : '保存失败'
  }
  finally {
    loading.value = false
  }
}

function handleClose() {
  emit('close')
}
</script>

<template>
  <div v-if="visible" class="dialog-mask" @click.self="handleClose">
    <div class="dialog">
      <div class="dialog__header">
        <div>
          <h3>{{ title }}</h3>
          <p>对接 Java 数据源管理服务</p>
        </div>
        <button type="button" class="ghost-button" @click="handleClose">关闭</button>
      </div>

      <div class="dialog__body">
        <label class="field">
          <span>名称</span>
          <input v-model="form.name" class="field-input" placeholder="例如：订单分析库" />
          <small v-if="errors.name" class="field-error">{{ errors.name }}</small>
        </label>

        <div class="field-grid">
          <label class="field">
            <span>类型</span>
            <select v-model="form.type" class="field-input">
              <option value="MYSQL">MySQL</option>
              <option value="CLICKHOUSE">ClickHouse</option>
              <option value="POSTGRES">Postgres</option>
            </select>
          </label>
          <label class="field">
            <span>只读</span>
            <label class="switch-field">
              <input v-model="form.readonly" type="checkbox">
              <span>{{ form.readonly ? '开启' : '关闭' }}</span>
            </label>
          </label>
        </div>

        <div class="field-grid field-grid--triple">
          <label class="field">
            <span>主机</span>
            <input v-model="form.host" class="field-input" placeholder="127.0.0.1" />
            <small v-if="errors.host" class="field-error">{{ errors.host }}</small>
          </label>
          <label class="field">
            <span>端口</span>
            <input v-model.number="form.port" class="field-input" type="number" />
            <small v-if="errors.port" class="field-error">{{ errors.port }}</small>
          </label>
          <label class="field">
            <span>数据库</span>
            <input v-model="form.database" class="field-input" placeholder="analytics" />
            <small v-if="errors.database" class="field-error">{{ errors.database }}</small>
          </label>
        </div>

        <div class="field-grid">
          <label class="field">
            <span>用户名</span>
            <input v-model="form.username" class="field-input" placeholder="reader" />
            <small v-if="errors.username" class="field-error">{{ errors.username }}</small>
          </label>
          <label class="field">
            <span>密码</span>
            <template v-if="isEdit">
              <label class="switch-field switch-field--inline">
                <input v-model="resetPassword" type="checkbox">
                <span>重置密码</span>
              </label>
              <input v-if="resetPassword" v-model="form.password" class="field-input" type="password" placeholder="请输入新密码" />
            </template>
            <input v-else v-model="form.password" class="field-input" type="password" placeholder="请输入密码" />
            <small v-if="errors.password" class="field-error">{{ errors.password }}</small>
          </label>
        </div>

        <label class="field">
          <span>JDBC 参数(JSON)</span>
          <textarea v-model="form.jdbcOptions" class="field-input field-input--textarea" placeholder='例如：{"ssl":"true"}' />
          <small v-if="errors.jdbcOptions" class="field-error">{{ errors.jdbcOptions }}</small>
        </label>

        <div v-if="submitError" class="alert alert--error">{{ submitError }}</div>
      </div>

      <div class="dialog__footer">
        <button type="button" class="ghost-button" :disabled="loading" @click="handleClose">取消</button>
        <button type="button" class="primary-button" :disabled="loading" @click="handleSubmit">
          {{ loading ? '提交中...' : '保存' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(2, 6, 23, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  z-index: 20;
}
.dialog {
  width: min(760px, 100%);
  border: 1px solid #1e293b;
  border-radius: 20px;
  background: #0f172a;
  box-shadow: 0 24px 80px rgba(15, 23, 42, 0.5);
}
.dialog__header,
.dialog__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
}
.dialog__header {
  border-bottom: 1px solid #1e293b;
}
.dialog__header h3 {
  margin: 0;
  font-size: 20px;
}
.dialog__header p {
  margin: 4px 0 0;
  color: #94a3b8;
}
.dialog__body {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 18px;
}
.dialog__footer {
  border-top: 1px solid #1e293b;
  gap: 12px;
}
.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.field-grid--triple {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.field span {
  color: #cbd5e1;
  font-size: 14px;
}
.field-input {
  width: 100%;
  border: 1px solid #334155;
  border-radius: 12px;
  background: #020617;
  color: #e2e8f0;
  padding: 10px 12px;
}
.field-input--textarea {
  min-height: 120px;
  resize: vertical;
}
.switch-field {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #cbd5e1;
  min-height: 42px;
}
.switch-field--inline {
  margin-bottom: 8px;
}
.field-error {
  color: #fca5a5;
}
.alert {
  border-radius: 12px;
  padding: 10px 12px;
  font-size: 14px;
}
.alert--error {
  background: rgba(127, 29, 29, 0.28);
  color: #fecaca;
  border: 1px solid rgba(248, 113, 113, 0.32);
}
.primary-button,
.ghost-button {
  border-radius: 12px;
  padding: 10px 16px;
  cursor: pointer;
}
.primary-button {
  border: none;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: #fff;
}
.ghost-button {
  border: 1px solid #334155;
  background: transparent;
  color: #cbd5e1;
}
@media (max-width: 900px) {
  .field-grid,
  .field-grid--triple {
    grid-template-columns: 1fr;
  }
}
</style>
