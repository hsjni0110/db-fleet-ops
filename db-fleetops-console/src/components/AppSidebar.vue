<script setup lang="ts">
import { useRouter } from "vue-router";
import {
  Connection,
  DataAnalysis,
  Files,
  Monitor,
  Operation,
  Platform,
  SetUp,
  Warning,
} from "@element-plus/icons-vue";

const router = useRouter();
const appName = import.meta.env.VITE_APP_NAME || "DB FleetOps";

const menus = [
  { path: "/dashboard", icon: DataAnalysis },
  { path: "/databases", icon: Platform },
  { path: "/jobs", icon: Operation },
  { path: "/restore-verifications", icon: Files },
  { path: "/agents", icon: Monitor },
  { path: "/configuration-profiles", icon: SetUp },
  { path: "/configuration-drifts", icon: Warning },
];
</script>

<template>
  <aside class="sidebar">
    <RouterLink to="/dashboard" class="brand">
      <span class="brand-icon" aria-hidden="true">
        <el-icon><Connection /></el-icon>
      </span>
      <strong>{{ appName }}</strong>
    </RouterLink>

    <nav class="sidebar-nav" aria-label="운영 메뉴">
      <RouterLink
        v-for="menu in menus"
        :key="menu.path"
        :to="menu.path"
        class="nav-item"
        active-class="active"
      >
        <el-icon class="nav-icon" aria-hidden="true">
          <component :is="menu.icon" />
        </el-icon>
        {{ router.resolve(menu.path).meta.title }}
      </RouterLink>
    </nav>
  </aside>
</template>
