import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'normalize.css/normalize.css'
import App from './App.vue'
import { setupStore } from './store'
import router from './router'
import './permission'

const app = createApp(App)

setupStore(app)
app.use(router)
app.use(ElementPlus, { size: 'small' })

app.mount('#app')
