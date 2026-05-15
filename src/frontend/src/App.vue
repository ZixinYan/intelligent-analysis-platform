<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useNodeRegistryStore } from '@/stores/node-registry'
import { useDatasourceStore } from '@/stores/datasource'
import WorkflowEditor from '@/components/workflow/WorkflowEditor.vue'
import DatasourceList from '@/components/datasource/DatasourceList.vue'
import QueryPlayground from '@/components/query/QueryPlayground.vue'
import DatasetList from '@/components/dataset/DatasetList.vue'
import AiChatSidebar from '@/components/ai/AiChatSidebar.vue'
import ApprovalsView from '@/components/approvals/ApprovalsView.vue'
import OpsView from '@/components/ops/OpsView.vue'

const registry = useNodeRegistryStore()
const datasourceStore = useDatasourceStore()
const currentView = ref<'workflow' | 'datasource' | 'query' | 'dataset' | 'approvals' | 'ops'>('workflow')

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
      <button class="app-nav__button" :class="{ 'app-nav__button--active': currentView === 'dataset' }" @click="currentView = 'dataset'">
        数据集
      </button>
      <button class="app-nav__button" :class="{ 'app-nav__button--active': currentView === 'approvals' }" @click="currentView = 'approvals'">
        审批管理
      </button>
      <button class="app-nav__button" :class="{ 'app-nav__button--active': currentView === 'ops' }" @click="currentView = 'ops'">
        运维监控
      </button>
    </header>

    <WorkflowEditor v-if="currentView === 'workflow'" />
    <DatasourceList v-else-if="currentView === 'datasource'" />
    <QueryPlayground v-else-if="currentView === 'query'" />
    <DatasetList v-else-if="currentView === 'dataset'" />
    <ApprovalsView v-else-if="currentView === 'approvals'" />
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
