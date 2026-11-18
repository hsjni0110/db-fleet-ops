<script setup lang="ts">
defineProps<{ label: string; text: string }>();
</script>

<template>
  <el-tooltip :content="text" placement="top" :trigger="['hover', 'focus', 'click']">
    <button type="button" class="field-help" :aria-label="`${label} 도움말`">?</button>
  </el-tooltip>
</template>
