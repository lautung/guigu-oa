import Vue from 'vue'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css'
import 'normalize.css/normalize.css'

import App from './App'
import store from './store'
import router from './router'
import '@/permission'

Vue.use(ElementUI, { size: 'small' })
Vue.config.productionTip = false

new Vue({
  el: '#app',
  router,
  store,
  render: h => h(App)
})
