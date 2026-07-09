<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import { CircleCheckFilled } from "@element-plus/icons-vue";

const route = useRoute();

const appEnv = import.meta.env.VITE_APP_ENV ?? "local";
const apiBaseUrl =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

const title = computed(() => {
  return String(route.meta.title ?? "DB FleetOps Console");
});

const description = computed(() => {
  return String(route.meta.description ?? "DB 운영 콘솔");
});
</script>

<template>
  <header class="console-header">
    <div>
      <h1>{{ title }}</h1>
      <p>{{ description }}</p>
    </div>

    <div class="header-actions">
      <el-tag type="info" effect="plain">{{ appEnv }}</el-tag>

      <el-tooltip :content="apiBaseUrl" placement="bottom">
        <el-tag type="success" effect="light">
          <el-icon>
            <CircleCheckFilled />
          </el-icon>
          Control Plane
        </el-tag>
      </el-tooltip>
    </div>
  </header>
</template>