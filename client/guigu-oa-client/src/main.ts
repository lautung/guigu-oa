import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import 'normalize.css/normalize.css'
import formCreate from '@form-create/element-ui'
import autoImport from '@form-create/element-ui/auto-import'
import FcDesigner from '@form-create/designer'
import App from './App.vue'
import { setupStore } from './store'
import router from './router'
import { setupNavigation } from './router/guard'
import { setupRecognition } from './recognition/browser-adapter'
import { hasPermi } from './permission'

formCreate.use(autoImport)

const app = createApp(App)

const pinia = setupStore(app)
const recognition = setupRecognition(pinia, router)
setupNavigation(router, pinia, recognition)
app.use(router)
app.directive('hasPermi', hasPermi)
app.use(ElementPlus, { size: 'small' })
app.use(formCreate)
app.use(FcDesigner)

app.mount('#app')
