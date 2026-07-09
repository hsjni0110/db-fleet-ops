import { createRouter, createWebHistory } from "vue-router";

import AppLayout from "../layouts/AppLayout.vue";
import DashboardPage from "../pages/DashboardPage.vue";
import DatabaseListPage from "../pages/DatabaseListPage.vue";
import DatabaseDetailPage from "../pages/DatabaseDetailPage.vue";
import OperationJobsPage from "../pages/OperationJobsPage.vue";
import OperationJobDetailPage from "../pages/OperationJobDetailPage.vue";
import AgentsPage from "../pages/AgentsPage.vue";
import AgentDetailPage from "../pages/AgentDetailPage.vue";
import AlertsPage from "../pages/AlertsPage.vue";
import AlertDetailPage from "../pages/AlertDetailPage.vue";
import ConfigurationProfilesPage from "../pages/ConfigurationProfilesPage.vue";
import ConfigurationDriftsPage from "../pages/ConfigurationDriftsPage.vue";
import RestoreVerificationsPage from "../pages/RestoreVerificationsPage.vue";
import NotFoundPage from "../pages/NotFoundPage.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      component: AppLayout,
      redirect: "/dashboard",
      children: [
        {
          path: "dashboard",
          name: "Dashboard",
          component: DashboardPage,
          meta: {
            title: "Dashboard",
            description: "전체 DB 운영 상태를 요약합니다.",
          },
        },
        {
          path: "databases",
          name: "Databases",
          component: DatabaseListPage,
          meta: {
            title: "Databases",
            description: "등록된 DB 인스턴스 목록을 확인합니다.",
          },
        },
        {
          path: "databases/:databaseId",
          name: "DatabaseDetail",
          component: DatabaseDetailPage,
          meta: {
            title: "Database Detail",
            description: "DB 상세, Health, Diagnostics, Drift, Capability를 확인합니다.",
          },
        },
        {
          path: "jobs",
          name: "OperationJobs",
          component: OperationJobsPage,
          meta: {
            title: "Operation Jobs",
            description: "백업, 설정 점검, 설정 적용 등 운영 Job 상태를 추적합니다.",
          },
        },
        {
          path: "jobs/:jobId",
          name: "OperationJobDetail",
          component: OperationJobDetailPage,
          meta: {
            title: "Job Detail",
            description: "Job 상세, Task, 결과, 복원 검증 결과를 확인합니다.",
          },
        },
        {
          path: "agents",
          name: "Agents",
          component: AgentsPage,
          meta: {
            title: "Agents",
            description: "Agent 상태와 Heartbeat 정보를 확인합니다.",
          },
        },
        {
          path: "agents/:agentId",
          name: "AgentDetail",
          component: AgentDetailPage,
          meta: {
            title: "Agent Detail",
            description: "Agent 상세와 최근 Host Metric을 확인합니다.",
          },
        },
        {
          path: "alerts",
          name: "Alerts",
          component: AlertsPage,
          meta: {
            title: "Alerts",
            description: "운영 Alert를 확인하고 Ack / Resolve 처리합니다.",
          },
        },
        {
          path: "alerts/:alertId",
          name: "AlertDetail",
          component: AlertDetailPage,
          meta: {
            title: "Alert Detail",
            description: "Alert 상세와 조치 정보를 확인합니다.",
          },
        },
        {
          path: "configuration-profiles",
          name: "ConfigurationProfiles",
          component: ConfigurationProfilesPage,
          meta: {
            title: "Configuration Profiles",
            description: "DB 설정 기준 Profile을 관리합니다.",
          },
        },
        {
          path: "configuration-drifts",
          name: "ConfigurationDrifts",
          component: ConfigurationDriftsPage,
          meta: {
            title: "Configuration Drifts",
            description: "실제 DB 설정과 표준 Profile의 차이를 확인합니다.",
          },
        },
        {
          path: "restore-verifications",
          name: "RestoreVerifications",
          component: RestoreVerificationsPage,
          meta: {
            title: "Restore Verifications",
            description: "백업 복원 검증 결과를 확인합니다.",
          },
        },
      ],
    },
    {
      path: "/:pathMatch(.*)*",
      name: "NotFound",
      component: NotFoundPage,
    },
  ],
});