/**
 * Backward-compatible facade over the three focused stores.
 *
 * New code should import the dedicated stores directly:
 *   - useWorkflowDefinitionStore  (CRUD, metadata)
 *   - useWorkflowGraphStore       (Vue-Flow canvas state)
 *   - useWorkflowDebugStore       (debug run state)
 *
 * Existing components can continue to call useWorkflowStore() unchanged.
 * Note: storeToRefs() is not supported on this facade — use individual stores
 * or access properties directly (e.g. `const store = useWorkflowStore(); store.nodes`).
 */
import type { WorkflowDefinitionDTO } from '@/types/contract'
import type { WorkflowNode, WorkflowViewport } from '@/types/workflow'
import { useWorkflowDefinitionStore } from './workflowDefinition'
import { useWorkflowDebugStore } from './workflowDebug'
import { useWorkflowGraphStore } from './workflowGraph'

export { useWorkflowDefinitionStore, useWorkflowGraphStore, useWorkflowDebugStore }

type WorkflowStoreFacade =
  Omit<ReturnType<typeof useWorkflowDefinitionStore>, '$id' | 'hydrate'> &
  Omit<ReturnType<typeof useWorkflowGraphStore>, '$id' | 'onNodeClick' | 'setViewport' | 'serialize'> &
  Omit<ReturnType<typeof useWorkflowDebugStore>, '$id'> &
  {
    $id: string
    hydrate: (definition: WorkflowDefinitionDTO) => void
    onNodeClick: (payload: { node: WorkflowNode }) => void
    serialize: () => ReturnType<ReturnType<typeof useWorkflowGraphStore>['serialize']>
    setViewport: (nextViewport: WorkflowViewport) => void
  }

export function useWorkflowStore(): WorkflowStoreFacade {
  const def = useWorkflowDefinitionStore()
  const graph = useWorkflowGraphStore()
  const debug = useWorkflowDebugStore()

  /** Combined handler: update graph selection AND reset debug tab to config. */
  function onNodeClick(payload: { node: WorkflowNode }) {
    if (!graph.selectedNodeIds.includes(payload.node.id) || graph.selectedNodeIds.length !== 1 || graph.selectedEdgeIds.length > 0) {
      graph.setSingleNodeSelection(payload.node.id)
    }
    debug.setDebugTab('config')
  }

  /** Hydrate both metadata and graph state from a workflow definition. */
  function hydrate(definition: WorkflowDefinitionDTO) {
    def.workflowId = definition.workflowId
    def.workflowName = definition.workflowName || '未命名工作流'
    graph.hydrateGraph(definition)
  }

  /** Serialize the current graph using the stored workflow name. */
  function serialize() {
    return graph.serialize(def.workflowName)
  }

  /** setViewport wrapper that persists to localStorage keyed by current workflowId. */
  function setViewport(nextViewport: WorkflowViewport) {
    graph.setViewport(nextViewport, def.workflowId)
  }

  return { ...def, ...graph, ...debug, onNodeClick, hydrate, serialize, setViewport, reset: def.reset } as unknown as WorkflowStoreFacade
}
