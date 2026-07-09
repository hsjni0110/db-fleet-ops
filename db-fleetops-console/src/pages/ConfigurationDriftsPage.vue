<template>
  <section class="page-stack">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>Configuration Drifts</span>
          <el-tag type="warning">Actual vs Expected</el-tag>
        </div>
      </template>

      <p class="page-description">
        DB 실제 설정값과 Configuration Profile 기준값의 차이를 표시하는 화면입니다.
        최신 Drift와 Drift Item 상세를 연결할 예정입니다.
      </p>

      <el-empty description="Configuration drift results will be implemented after API client setup." />
    </el-card>
  </section>
</template>