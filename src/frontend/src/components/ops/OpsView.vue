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
    <!-- 页头 -->
    <header class="page-header">
      <div>
        <h2>运维监控</h2>
        <p>查询执行概况 · 每 30 秒自动刷新</p>
      </div>
      <div v-if="lastUpdated > 0" class="last-updated">
        最后更新：{{ formatTime(lastUpdated) }}
      </div>
    </header>

    <!-- 内容 -->
    <div v-if="loading && !metrics" class="page-state">加载中...</div>
    <div v-else-if="error" class="page-state page-state--error">{{ error }}</div>
    <div v-else-if="metrics" class="content">
      <!-- 指标卡片 -->
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

      <!-- 慢查询 Top N -->
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

      <!-- 扩展提示 -->
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
}
.page-header p {
  margin: 6px 0 0;
  color: #94a3b8;
  font-size: 13px;
}
.last-updated {
  font-size: 12px;
  color: #475569;
  white-space: nowrap;
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
  border: 1px solid #1e293b;
  border-radius: 16px;
  background: rgba(15, 23, 42, 0.7);
  padding: 20px;
}
.metric-label {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 10px;
}
.metric-value {
  font-size: 28px;
  font-weight: 700;
  color: #e2e8f0;
}
.metric-card--accent .metric-value {
  color: #fbbf24;
}
.metric-unit {
  font-size: 13px;
  font-weight: 400;
  color: #64748b;
  margin-left: 4px;
}
.section-title {
  font-size: 13px;
  font-weight: 500;
  color: #94a3b8;
  margin-bottom: 12px;
}
.table-wrap {
  overflow: auto;
  border: 1px solid #1e293b;
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
  border-bottom: 1px solid #1e293b;
}
.slow-table th {
  color: #94a3b8;
  background: rgba(15, 23, 42, 0.95);
  font-weight: 500;
  white-space: nowrap;
}
.slow-table td {
  color: #e2e8f0;
}
.slow-table tbody tr:last-child td {
  border-bottom: none;
}
.slow-table tbody tr:hover td {
  background: rgba(255, 255, 255, 0.025);
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
  color: #64748b !important;
}
.cell-warning {
  color: #fbbf24 !important;
  font-weight: 500;
}
.ext-hint {
  border: 1px dashed #1e293b;
  border-radius: 12px;
  padding: 16px;
  font-size: 13px;
  color: #475569;
  text-align: center;
}
</style>
