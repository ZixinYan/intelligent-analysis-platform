<script setup lang="ts">
import { computed } from 'vue'

export type AppIconName =
  | 'ai'
  | 'analysis'
  | 'branch'
  | 'chart'
  | 'check'
  | 'chevron-left'
  | 'chevron-right'
  | 'close'
  | 'code'
  | 'filter'
  | 'function'
  | 'info'
  | 'loop'
  | 'package'
  | 'pivot'
  | 'play'
  | 'search'
  | 'shield'
  | 'sigma'
  | 'sort'
  | 'table'
  | 'trend'
  | 'x'

const props = withDefaults(defineProps<{
  name: AppIconName
  size?: number | string
  strokeWidth?: number | string
}>(), {
  size: 16,
  strokeWidth: 1.8,
})

const iconMap: Record<AppIconName, string[]> = {
  ai: [
    'M12 3.75 13.35 6.65 16.25 8l-2.9 1.35L12 12.25l-1.35-2.9L7.75 8l2.9-1.35z',
    'M5.25 13.25 6 14.9l1.65.75L6 16.4l-.75 1.6-.75-1.6L2.85 15.65 4.5 14.9z',
  ],
  analysis: [
    'M4 14.5h12',
    'M6 12V9.5',
    'M10 12V6.5',
    'M14 12V4.5',
    'M18 12V8',
  ],
  branch: [
    'M7 5.5v7.5a2.5 2.5 0 0 0 2.5 2.5H16',
    'M7 5.5a2 2 0 1 0-4 0 2 2 0 0 0 4 0Z',
    'M21 8.5a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z',
    'M21 15.5a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z',
    'M9.5 13h5.5',
  ],
  chart: [
    'M4 18.5h16',
    'M7 16V10.5',
    'M12 16V6.5',
    'M17 16V12.5',
  ],
  check: ['M5.5 12.5 9.25 16 18.5 7.5'],
  'chevron-left': ['M14.5 6 8.5 12l6 6'],
  'chevron-right': ['M9.5 6 15.5 12l-6 6'],
  close: ['M6 6 18 18', 'M18 6 6 18'],
  code: ['M9 8 4.5 12 9 16', 'M15 8 19.5 12 15 16', 'M13.5 5.5 10.5 18.5'],
  filter: ['M4.5 6.5h15l-6 6.25v4.75l-3-1.65v-3.1z'],
  function: ['M15.5 6h-4a2.5 2.5 0 0 0-2.5 2.5V18', 'M7.5 11.5h7'],
  info: ['M12 10.5v5', 'M12 7.5h.01', 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18Z'],
  loop: [
    'M4.5 9A7.5 7.5 0 0 1 19 9',
    'M19.5 6v3h-3',
    'M19.5 15A7.5 7.5 0 0 1 5 15',
    'M4.5 18v-3h3',
  ],
  package: [
    'm12 3 7 3.8v9.4L12 21l-7-8.8V6.8z',
    'M5 7.1 12 11l7-3.9',
    'M12 11v10',
  ],
  pivot: ['M5 5.5h6v6H5z', 'M13 5.5h6v6h-6z', 'M5 13h6v6H5z', 'M13 13h6v6h-6z'],
  play: ['M9 7.5v9l7-4.5z'],
  search: ['M11 18a7 7 0 1 1 0-14 7 7 0 0 1 0 14Z', 'm16 16 4 4'],
  shield: ['M12 3.5 18.5 6v5.5c0 4.1-2.6 7.1-6.5 9-3.9-1.9-6.5-4.9-6.5-9V6z'],
  sigma: ['M17.5 5.5h-9l5 6.5-5 6.5h9'],
  sort: ['M8 6.5v11', 'M8 17.5 5.5 15', 'M8 17.5 10.5 15', 'M16 17.5v-11', 'M16 6.5 13.5 9', 'M16 6.5 18.5 9'],
  table: ['M4.5 6.5h15v11h-15z', 'M4.5 10.5h15', 'M9.5 6.5v11'],
  trend: ['M5 16 10 11l3.25 3.25L19 8.5', 'M19 8.5v4.5', 'M19 8.5h-4.5'],
  x: ['M6 6 18 18', 'M18 6 6 18'],
}

const paths = computed(() => iconMap[props.name] ?? iconMap.package)
</script>

<template>
  <svg
    class="app-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <path
      v-for="(path, index) in paths"
      :key="`${name}-${index}`"
      :d="path"
      stroke="currentColor"
      :stroke-width="strokeWidth"
      stroke-linecap="round"
      stroke-linejoin="round"
    />
  </svg>
</template>

<style scoped>
.app-icon {
  display: block;
  flex-shrink: 0;
}
</style>
