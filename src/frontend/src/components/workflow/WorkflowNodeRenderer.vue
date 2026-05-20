<script setup lang="ts">
import type { WorkflowNode } from '@/types/workflow'
import { resolveNodeComponent } from '@/components/workflow/nodes'

const props = defineProps<{
  id: string
  data: WorkflowNode['data']
}>()

const component = resolveNodeComponent({ data: props.data })
</script>

<template>
  <component :is="component" :id="id" :data="data" />
</template>
