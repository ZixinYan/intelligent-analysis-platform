<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useNodeRegistryStore } from '@/stores/node-registry'
import { useDatasourceStore } from '@/stores/datasource'
import WorkflowEditor from '@/components/workflow/WorkflowEditor.vue'
import DatasourceList from '@/components/datasource/DatasourceList.vue'
import AiChatSidebar from '@/components/ai/AiChatSidebar.vue'
import OpsView from '@/components/ops/OpsView.vue'

const registry = useNodeRegistryStore()
const datasourceStore = useDatasourceStore()
const currentView = ref<'workflow' | 'datasource' | 'ops'>('workflow')
const theme = ref<'light' | 'dark'>('dark')
const THEME_KEY = 'iap-theme'

function applyTheme(nextTheme: 'light' | 'dark') {
  const root = document.documentElement
  root.setAttribute('data-changing-theme', 'true')
  root.setAttribute('data-theme', nextTheme)
  window.localStorage.setItem(THEME_KEY, nextTheme)
  window.setTimeout(() => root.removeAttribute('data-changing-theme'), 0)
}

function toggleTheme() {
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
}

onMounted(() => {
  registry.load().catch(() => undefined)
  datasourceStore.load().catch(() => undefined)

  const savedTheme = window.localStorage.getItem(THEME_KEY)
  theme.value = savedTheme === 'light' ? 'light' : 'dark'
  applyTheme(theme.value)
})

watch(theme, value => applyTheme(value))
</script>

<template>
  <div class="app-shell">
    <header class="app-nav">
      <div class="app-nav__tabs">
        <button class="app-nav__button" :class="{ 'app-nav__button--active': currentView === 'workflow' }" @click="currentView = 'workflow'">
          工作流编辑
        </button>
        <button class="app-nav__button" :class="{ 'app-nav__button--active': currentView === 'datasource' }" @click="currentView = 'datasource'">
          数据源管理
        </button>
        <button class="app-nav__button" :class="{ 'app-nav__button--active': currentView === 'ops' }" @click="currentView = 'ops'">
          运维监控
        </button>
      </div>
      <button class="app-nav__button app-nav__button--theme" @click="toggleTheme">
        {{ theme === 'dark' ? '浅色' : '深色' }}
      </button>
    </header>

    <WorkflowEditor v-if="currentView === 'workflow'" />
    <DatasourceList v-else-if="currentView === 'datasource'" />
    <OpsView v-else-if="currentView === 'ops'" />

    <AiChatSidebar />
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
}
.app-nav {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--iap-divider);
  background: var(--iap-nav-bg);
  backdrop-filter: blur(18px);
  box-shadow: var(--iap-shadow-panel);
}
.app-nav__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.app-nav__button {
  border: 1px solid var(--iap-btn-secondary-border);
  border-radius: 999px;
  background: var(--iap-btn-secondary-bg);
  color: var(--iap-btn-secondary-text);
  padding: 10px 16px;
  cursor: pointer;
}
.app-nav__button:hover {
  border-color: var(--iap-divider-strong);
  background: var(--iap-btn-secondary-hover);
  color: var(--iap-btn-secondary-text-strong);
}
.app-nav__button--active {
  border-color: transparent;
  background: var(--iap-btn-primary-bg);
  color: var(--iap-btn-primary-text);
  box-shadow: 0 8px 20px var(--iap-accent-ring);
}
.app-nav__button--theme {
  min-width: 72px;
}
</style>
