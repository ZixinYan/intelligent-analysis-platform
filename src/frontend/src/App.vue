<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useNodeRegistryStore } from '@/stores/node-registry'
import { useDatasourceStore } from '@/stores/datasource'
import WorkflowEditor from '@/components/workflow/WorkflowEditor.vue'
import DatasourceList from '@/components/datasource/DatasourceList.vue'
import QueryPlayground from '@/components/query/QueryPlayground.vue'

const registry = useNodeRegistryStore()
const datasourceStore = useDatasourceStore()
const currentView = ref<'workflow' | 'datasource' | 'query'>('workflow')

onMounted(() => {
  registry.load().catch(() => undefined)
  datasourceStore.load().catch(() => undefined)
})
</script>

<template>
  <div class="app-shell">
    <header class="app-nav">
      <button class="app-nav__button" :class="{ 'app-nav__button--active': currentView === 'workflow' }" @click="currentView = 'workflow'">
        工作流编辑
      </button>
      <button class="app-nav__button" :class="{ 'app-nav__button--active': currentView === 'datasource' }" @click="currentView = 'datasource'">
        数据源管理
      </button>
      <button class="app-nav__button" :class="{ 'app-nav__button--active': currentView === 'query' }" @click="currentView = 'query'">
        SQL 查询
      </button>
    </header>

    <WorkflowEditor v-if="currentView === 'workflow'" />
    <DatasourceList v-else-if="currentView === 'datasource'" />
    <QueryPlayground v-else />
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
}
.app-nav {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid #1e293b;
  background: rgba(2, 6, 23, 0.92);
  backdrop-filter: blur(12px);
}
.app-nav__button {
  border: 1px solid #334155;
  border-radius: 999px;
  background: transparent;
  color: #cbd5e1;
  padding: 10px 16px;
  cursor: pointer;
}
.app-nav__button--active {
  border-color: transparent;
  background: linear-gradient(135deg, #2563eb, #7c3aed);
  color: #fff;
}
</style>
