<template>
  <main class="not-found-page">
    <el-result
      icon="warning"
      title="Page Not Found"
      sub-title="요청한 운영 콘솔 페이지를 찾을 수 없습니다."
    >
      <template #extra>
        <RouterLink to="/dashboard">
          <el-button type="primary">Go Dashboard</el-button>
        </RouterLink>
      </template>
    </el-result>
  </main>
</template>