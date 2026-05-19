<script setup lang="ts">
import { computed } from 'vue'
import type { WorkflowNode } from '@/types/workflow'
import { resolvePanelComponent } from '@/components/workflow/nodes'

const props = defineProps<{
  node?: WorkflowNode
}>()

const component = computed(() => resolvePanelComponent(props.node))
</script>

<template>
  <component :is="component" :node="node" />
</template>
