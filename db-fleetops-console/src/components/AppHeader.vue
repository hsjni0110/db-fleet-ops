<script setup lang="ts">
import { useRoute } from "vue-router";

const route = useRoute();
</script>

<template>
  <header class="console-header">
    <h1>{{ route.meta.title }}</h1>
  </header>
</template>
