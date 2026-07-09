<script setup lang="ts">
const appName = import.meta.env.VITE_APP_NAME ?? "DB FleetOps Console";
const appEnv = import.meta.env.VITE_APP_ENV ?? "local";
const apiBaseUrl =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
</script>

<template>
  <main class="app-shell">
    <section class="hero-card">
      <div class="eyebrow">Vue Dashboard</div>

      <h1>{{ appName }}</h1>

      <p class="description">
        DB FleetOps 운영 콘솔을 위한 Vue 3 기반 대시보드 프로젝트입니다.
        이 화면은 프론트엔드 실행 환경이
        정상적으로 구성되었는지 확인하기 위한 초기 화면입니다.
      </p>

      <div class="meta-row">
        <el-tag type="success" effect="dark">Vue 3</el-tag>
        <el-tag type="primary" effect="dark">Vite</el-tag>
        <el-tag type="warning" effect="dark">TypeScript</el-tag>
        <el-tag type="info" effect="dark">Element Plus</el-tag>
      </div>

      <div class="info-grid">
        <div class="info-card">
          <span class="label">Environment</span>
          <strong>{{ appEnv }}</strong>
        </div>

        <div class="info-card">
          <span class="label">API Base URL</span>
          <strong>{{ apiBaseUrl }}</strong>
        </div>
      </div>
    </section>
  </main>
</template>