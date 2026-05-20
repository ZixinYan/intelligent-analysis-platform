import type { NodeMetaDTO } from '@/types/contract'
import type { NodeCatalogGroup } from './insert-types'

export const nodeCategoryMeta: Record<string, { label: string; color: string; desc: string }> = {
  QUERY: { label: '取数', color: '#3b82f6', desc: '连接数据源，执行查询' },
  COMPUTE: { label: '计算', color: '#8b5cf6', desc: '数据转换与运算' },
  OUTPUT: { label: '输出', color: '#10b981', desc: '渲染图表或表格' },
  GOVERNANCE: { label: '治理', color: '#f59e0b', desc: '数据质量与权限' },
  ANALYSIS: { label: '分析', color: '#06b6d4', desc: '数据分析智能节点' },
}

export function groupNodeCatalog(nodes: NodeMetaDTO[]): NodeCatalogGroup[] {
  const groups: Record<string, NodeMetaDTO[]> = {}
  for (const node of nodes) {
    const category = node.category ?? 'OTHER'
    if (!groups[category]) groups[category] = []
    groups[category].push(node)
  }
  return Object.entries(groups).map(([category, groupedNodes]) => ({
    category,
    meta: nodeCategoryMeta[category] ?? { label: category, color: '#64748b', desc: '' },
    nodes: groupedNodes,
  }))
}

export function shortNodeDesc(desc?: string): string {
  if (!desc) return ''
  const firstClause = desc.split(/[；，、]/)[0]
  return firstClause.length > 22 ? `${firstClause.slice(0, 22)}…` : firstClause
}
