import {createApp} from 'vue'
import App from './App.vue'
import router from './router'
import store from './store'
import {
  create,
  NAvatar,
  NInput,
  NIcon,
  NTag,
  NDivider,
  NButton,
  NDrawer,
  NCard,
  NTabs,
  NTabPane,
  NSwitch,
  NModal,
  NBadge,
  NPopover,
  NImage,
  NPopconfirm
} from 'naive-ui'

import {
  ElUpload,
  ElButton,
  ElRadioGroup,
  ElRadioButton
} from 'element-plus'
import 'element-plus/dist/index.css'

import http from './utils/request'
import common from './utils/common'
import constant from './utils/constant'
// 导入字体加载器
import { loadFonts } from './utils/font-loader'

import 'vfonts/FiraCode.css'
import './assets/css/index.css'
import './assets/css/color.css'
import './assets/css/animation.css'

const naive = create({
  components: [NAvatar, NInput, NIcon, NTag, NDivider, NButton,
    NDrawer, NCard, NTabs, NTabPane, NSwitch, NModal, NBadge,
    NPopover, NImage, NPopconfirm]
})

const app = createApp(App)
app.use(router)
app.use(store)
app.use(naive)

app.component(ElUpload.name, ElUpload)
app.component(ElButton.name, ElButton)
app.component(ElRadioGroup.name, ElRadioGroup)
app.component(ElRadioButton.name, ElRadioButton)

app.config.globalProperties.$http = http
app.config.globalProperties.$common = common
app.config.globalProperties.$constant = constant

// 创建事件总线
app.config.globalProperties.$bus = {
  // 简单的事件总线实现
  _events: {},
  $on(event, callback) {
    if (!this._events[event]) {
      this._events[event] = [];
    }
    this._events[event].push(callback);
    return this;
  },
  $off(event, callback) {
    if (!this._events[event]) return this;
    if (!callback) {
      this._events[event] = [];
      return this;
    }
    this._events[event] = this._events[event].filter(fn => fn !== callback);
    return this;
  },
  $emit(event, ...args) {
    if (!this._events[event]) return this;
    const cbs = [...this._events[event]];
    cbs.forEach(cb => cb(...args));
    return this;
  }
};

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth) {
    if (to.path === "/") {
      if (typeof to.query.defaultStoreType !== "undefined") {
        localStorage.setItem("defaultStoreType", to.query.defaultStoreType);
      }
      if (typeof to.query.userToken !== "undefined") {
        let userToken = to.query.userToken;
        const xhr = new XMLHttpRequest();
        xhr.open('post', constant.baseURL + "/user/token", false);
        xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
        xhr.send("userToken=" + userToken);
        let result = JSON.parse(xhr.responseText);
        if (!common.isEmpty(result) && result.code === 200) {
          store.commit("loadCurrentUser", result.data);
          localStorage.setItem("userToken", result.data.accessToken);
          window.location.href = constant.imURL;
          next();
        } else {
          window.location.href = constant.webBaseURL;
        }
      } else if (Boolean(localStorage.getItem("userToken"))) {
        next();
      } else {
        window.location.href = constant.webBaseURL;
      }
    } else {
      if (Boolean(localStorage.getItem("userToken"))) {
        next();
      } else {
        window.location.href = constant.webBaseURL;
      }
    }
  } else {
    next();
  }
})

// 添加全局错误处理器
app.config.errorHandler = (err, vm, info) => {
  // 检查是否为appendChild错误
  if (err.message && (err.message.includes('appendChild') || err.message.includes('node type'))) {
    console.warn('非关键DOM操作错误已被捕获（不影响功能）:', err.message);
    // 不进一步处理，避免影响用户体验
    return;
  }
  
  // 其他错误正常记录
  console.error('应用错误:', err);
  console.error('错误信息:', info);
};

// 捕获未处理的Promise错误
window.addEventListener('unhandledrejection', event => {
  console.warn('未处理的Promise错误:', event.reason);
  // 如果是appendChild相关错误，防止进一步传播
  if (event.reason && event.reason.toString && 
      (event.reason.toString().includes('appendChild') || 
       event.reason.toString().includes('node type'))) {
    console.warn('非关键Promise错误已被捕获（不影响功能）');
    event.preventDefault();
  }
});

// 初始加载字体
if (store.state.sysConfig) {
  loadFonts(store.state.sysConfig).catch(err => {
    console.error('加载字体失败:', err);
  });
}

// 监听系统配置变化，重新加载字体
store.watch(
  (state) => state.sysConfig,
  (newConfig) => {
    if (newConfig) {
      console.log('系统配置更新，重新加载字体...');
      loadFonts(newConfig).catch(err => {
        console.error('加载字体失败:', err);
      });
    }
  },
  { deep: true }
);

app.mount('#app')
