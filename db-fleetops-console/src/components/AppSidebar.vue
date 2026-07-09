<script setup lang="ts">
import {
  Bell,
  Connection,
  DataAnalysis,
  Files,
  Monitor,
  Operation,
  Platform,
  SetUp,
  Warning,
} from "@element-plus/icons-vue";

const appName = import.meta.env.VITE_APP_NAME ?? "DB FleetOps Console";

const menus = [
  {
    path: "/dashboard",
    label: "Dashboard",
    description: "운영 상태 요약",
    icon: DataAnalysis,
  },
  {
    path: "/databases",
    label: "Databases",
    description: "DB Inventory / Health",
    icon: Platform,
  },
  {
    path: "/jobs",
    label: "Operation Jobs",
    description: "Backup / Apply / Check",
    icon: Operation,
  },
  {
    path: "/agents",
    label: "Agents",
    description: "Heartbeat / Host Metric",
    icon: Monitor,
  },
  {
    path: "/alerts",
    label: "Alerts",
    description: "Ack / Resolve",
    icon: Bell,
  },
  {
    path: "/configuration-profiles",
    label: "Configuration",
    description: "Profile / Desired State",
    icon: SetUp,
  },
  {
    path: "/configuration-drifts",
    label: "Drifts",
    description: "Actual vs Expected",
    icon: Warning,
  },
  {
    path: "/restore-verifications",
    label: "Restore Verify",
    description: "Backup 검증 결과",
    icon: Files,
  },
];
</script>

<template>
  <aside class="sidebar">
    <div class="brand">
      <div class="brand-icon">
        <el-icon>
          <Connection />
        </el-icon>
      </div>

      <div>
        <strong>{{ appName }}</strong>
        <span>Operations Console</span>
      </div>
    </div>

    <nav class="sidebar-nav">
      <RouterLink
        v-for="menu in menus"
        :key="menu.path"
        :to="menu.path"
        class="nav-item"
        active-class="active"
      >
        <el-icon class="nav-icon">
          <component :is="menu.icon" />
        </el-icon>

        <span class="nav-text">
          <strong>{{ menu.label }}</strong>
          <small>{{ menu.description }}</small>
        </span>
      </RouterLink>
    </nav>
  </aside>
</template>