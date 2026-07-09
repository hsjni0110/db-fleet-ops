<script setup lang="ts">
import AppHeader from "../components/AppHeader.vue";
import AppSidebar from "../components/AppSidebar.vue";
</script>

<template>
  <div class="console-layout">
    <AppSidebar />

    <section class="console-main">
      <AppHeader />

      <main class="console-content">
        <router-view />
      </main>
    </section>
  </div>
</template>