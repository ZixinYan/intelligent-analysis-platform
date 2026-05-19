import { computed } from 'vue'
import { getBusinessNodeType } from '@/adapters/workflow-graph'
import AnalysisNodeShell from '@/components/workflow/AnalysisNodeShell.vue'
import NodeConfigPanel from '@/components/workflow/NodeConfigPanel.vue'

export const NodeComponentMap = {
  default: AnalysisNodeShell,
  sql_query: AnalysisNodeShell,
  aggregate: AnalysisNodeShell,
  filter: AnalysisNodeShell,
  sort: AnalysisNodeShell,
  formula: AnalysisNodeShell,
  pivot: AnalysisNodeShell,
  time_series_compute: AnalysisNodeShell,
  chart_output: AnalysisNodeShell,
  table_output: AnalysisNodeShell,
  condition: AnalysisNodeShell,
} as const

export const PanelComponentMap = {
  default: NodeConfigPanel,
  sql_query: NodeConfigPanel,
  aggregate: NodeConfigPanel,
  filter: NodeConfigPanel,
  sort: NodeConfigPanel,
  formula: NodeConfigPanel,
  pivot: NodeConfigPanel,
  time_series_compute: NodeConfigPanel,
  chart_output: NodeConfigPanel,
  table_output: NodeConfigPanel,
  condition: NodeConfigPanel,
} as const

export function resolveNodeComponent(node: { data: unknown }) {
  const type = getBusinessNodeType(node as never)
  return computed(() => NodeComponentMap[type as keyof typeof NodeComponentMap] ?? NodeComponentMap.default)
}

export function resolvePanelComponent(node?: { data: unknown }) {
  const type = node ? getBusinessNodeType(node as never) : ''
  return PanelComponentMap[type as keyof typeof PanelComponentMap] ?? PanelComponentMap.default
}
