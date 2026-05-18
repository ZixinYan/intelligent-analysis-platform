<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import type { OpsMetricsSummaryDTO } from '@/types/contract'
import { getOpsSummary } from '@/api/ops'

const metrics = ref<OpsMetricsSummaryDTO | null>(null)
const loading = ref(false)
const error = ref<string>()
const lastUpdated = ref(0)

async function load() {
  loading.value = true
  error.value = undefined
  try {
    metrics.value = await getOpsSummary()
    lastUpdated.value = Date.now()
  }
  catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
  }
  finally {
    loading.value = false
  }
}

function formatTime(ts: number) {
  return new Date(ts).toLocaleTimeString('zh-CN')
}

let timer: ReturnType<typeof setInterval>
onMounted(() => {
  load()
  timer = setInterval(load, 30_000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <section class="ops-page">
    <header class="page-header">
      <div>
        <h2>运维监控</h2>
        <p>查询执行概况 · 每 30 秒自动刷新</p>
      </div>
      <div v-if="lastUpdated > 0" class="last-updated">
        最后更新：{{ formatTime(lastUpdated) }}
      </div>
    </header>

    <div v-if="loading && !metrics" class="page-state">加载中...</div>
    <div v-else-if="error" class="page-state page-state--error">{{ error }}</div>
    <div v-else-if="metrics" class="content">
      <div class="metric-grid">
        <div class="metric-card" :class="{ 'metric-card--accent': metrics.activeQueryCount > 10 }">
          <div class="metric-label">活跃查询</div>
          <div class="metric-value">
            {{ metrics.activeQueryCount }}<span class="metric-unit">个</span>
          </div>
        </div>
        <div class="metric-card">
          <div class="metric-label">今日查询量</div>
          <div class="metric-value">
            {{ metrics.todayQueryCount.toLocaleString() }}<span class="metric-unit">次</span>
          </div>
        </div>
        <div class="metric-card" :class="{ 'metric-card--accent': metrics.avgElapsedMs > 5000 }">
          <div class="metric-label">平均耗时</div>
          <div class="metric-value">
            {{ metrics.avgElapsedMs }}<span class="metric-unit">ms</span>
          </div>
        </div>
        <div class="metric-card" :class="{ 'metric-card--accent': metrics.errorRate > 0.05 }">
          <div class="metric-label">错误率</div>
          <div class="metric-value">
            {{ (metrics.errorRate * 100).toFixed(2) }}<span class="metric-unit">%</span>
          </div>
        </div>
      </div>

      <div v-if="metrics.slowQueryTop && metrics.slowQueryTop.length > 0" class="slow-queries">
        <div class="section-title">慢查询 Top {{ metrics.slowQueryTop.length }}</div>
        <div class="table-wrap">
          <table class="slow-table">
            <thead>
              <tr>
                <th>SQL（摘要）</th>
                <th class="col-right col-w24">耗时 (ms)</th>
                <th class="col-right col-w16">次数</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(q, i) in metrics.slowQueryTop" :key="i">
                <td class="cell-mono cell-truncate">{{ q.sql }}</td>
                <td class="col-right cell-warning">{{ q.elapsedMs.toLocaleString() }}</td>
                <td class="col-right cell-muted">{{ q.count }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="ext-hint">后续可接入 Prometheus / Grafana 实时图表</div>
    </div>
  </section>
</template>

<style scoped>
.ops-page {
  min-height: calc(100vh - 72px);
  padding: 24px;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}
.page-header h2 {
  margin: 0;
  font-size: 24px;
  color: var(--iap-text-primary);
}
.page-header p {
  margin: 6px 0 0;
  color: var(--iap-text-tertiary);
  font-size: 13px;
}
.last-updated {
  font-size: 12px;
  color: var(--iap-text-tertiary);
  white-space: nowrap;
}
.page-state {
  border: 1px dashed var(--iap-divider-strong);
  border-radius: 16px;
  padding: 32px;
  color: var(--iap-text-tertiary);
  text-align: center;
  background: var(--iap-card-bg);
}
.page-state--error {
  color: var(--iap-error-text);
  border-color: var(--iap-error-border);
  background: var(--iap-error-bg);
}
.content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}
@media (min-width: 640px) {
  .metric-grid {
    grid-template-columns: repeat(4, 1fr);
  }
}
.metric-card {
  border: 1px solid var(--iap-card-border);
  border-radius: 16px;
  background: var(--iap-card-bg);
  padding: 20px;
  box-shadow: var(--iap-shadow-panel);
}
.metric-label {
  font-size: 12px;
  color: var(--iap-text-tertiary);
  margin-bottom: 10px;
}
.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--iap-text-primary);
}
.metric-card--accent .metric-value {
  color: var(--iap-warning-text);
}
.metric-unit {
  font-size: 13px;
  font-weight: 400;
  color: var(--iap-text-tertiary);
  margin-left: 4px;
}
.section-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--iap-text-secondary);
  margin-bottom: 12px;
}
.table-wrap {
  overflow: auto;
  border: 1px solid var(--iap-divider);
  border-radius: 12px;
}
.slow-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.slow-table th,
.slow-table td {
  padding: 10px 16px;
  text-align: left;
  border-bottom: 1px solid var(--iap-divider);
}
.slow-table th {
  color: var(--iap-text-secondary);
  background: var(--iap-surface-secondary);
  font-weight: 500;
  white-space: nowrap;
}
.slow-table td {
  color: var(--iap-text-primary);
}
.slow-table tbody tr:last-child td {
  border-bottom: none;
}
.slow-table tbody tr:hover td {
  background: var(--iap-surface-hover);
}
.col-right {
  text-align: right !important;
}
.col-w24 {
  width: 96px;
}
.col-w16 {
  width: 64px;
}
.cell-mono {
  font-family: ui-monospace, monospace;
}
.cell-truncate {
  max-width: 480px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.cell-muted {
  color: var(--iap-text-tertiary) !important;
}
.cell-warning {
  color: var(--iap-warning-text) !important;
  font-weight: 500;
}
.ext-hint {
  border: 1px dashed var(--iap-divider);
  border-radius: 12px;
  padding: 16px;
  font-size: 13px;
  color: var(--iap-text-tertiary);
  text-align: center;
  background: var(--iap-card-bg);
}
</style>
