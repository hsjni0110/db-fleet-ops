<template>
  <main class="not-found-page">
    <el-result
      icon="warning"
      title="페이지를 찾을 수 없음"
    >
      <template #extra>
        <RouterLink to="/dashboard" class="table-link">운영 현황</RouterLink>
      </template>
    </el-result>
  </main>
</template>