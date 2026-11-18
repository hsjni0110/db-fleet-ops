import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import ko from "element-plus/es/locale/lang/ko";
import "element-plus/dist/index.css";

import App from "./App.vue";
import { router } from "./router";
import "./styles/global.css";

const app = createApp(App);

app.use(createPinia());
app.use(router);
app.use(ElementPlus, { locale: ko });

app.mount("#app");