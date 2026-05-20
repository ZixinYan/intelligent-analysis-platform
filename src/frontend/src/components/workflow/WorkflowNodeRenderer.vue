<script setup lang="ts">
import type { WorkflowNode } from '@/types/workflow'
import { resolveNodeComponent } from '@/components/workflow/nodes'
import type { WorkflowInsertTrigger } from './insert-types'

const props = defineProps<{
  id: string
  data: WorkflowNode['data']
}>()

const emit = defineEmits<{
  'open-insert-picker': [trigger: WorkflowInsertTrigger]
}>()

const component = resolveNodeComponent({ data: props.data })
</script>

<template>
  <component :is="component" :id="id" :data="data" @open-insert-picker="emit('open-insert-picker', $event)" />
</template>
