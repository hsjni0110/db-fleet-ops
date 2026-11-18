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
          meta: { title: "운영 현황" },
        },
        {
          path: "databases",
          name: "Databases",
          component: DatabaseListPage,
          meta: { title: "데이터베이스 목록" },
        },
        {
          path: "databases/:databaseId",
          name: "DatabaseDetail",
          component: DatabaseDetailPage,
          meta: { title: "데이터베이스 상세" },
        },
        {
          path: "jobs",
          name: "OperationJobs",
          component: OperationJobsPage,
          meta: { title: "운영 작업" },
        },
        {
          path: "jobs/:jobId",
          name: "OperationJobDetail",
          component: OperationJobDetailPage,
          meta: { title: "운영 작업 상세" },
        },
        {
          path: "agents",
          name: "Agents",
          component: AgentsPage,
          meta: { title: "실행기 목록" },
        },
        {
          path: "agents/:agentId",
          name: "AgentDetail",
          component: AgentDetailPage,
          meta: { title: "실행기 상세" },
        },
        {
          path: "alerts",
          name: "Alerts",
          component: AlertsPage,
          meta: { title: "운영 알림" },
        },
        {
          path: "alerts/:alertId",
          name: "AlertDetail",
          component: AlertDetailPage,
          meta: { title: "알림 상세" },
        },
        {
          path: "configuration-profiles",
          name: "ConfigurationProfiles",
          component: ConfigurationProfilesPage,
          meta: { title: "설정 기준" },
        },
        {
          path: "configuration-drifts",
          name: "ConfigurationDrifts",
          component: ConfigurationDriftsPage,
          meta: { title: "설정 차이" },
        },
        {
          path: "restore-verifications",
          name: "RestoreVerifications",
          component: RestoreVerificationsPage,
          meta: { title: "복원 검증" },
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