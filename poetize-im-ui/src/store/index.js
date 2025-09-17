import {createStore} from 'vuex'


export default createStore({
  state: {
    currentUser: JSON.parse(localStorage.getItem("currentUser") || '{}'),
    sysConfig: JSON.parse(localStorage.getItem("sysConfig") || '{}'),
    onlineUserCount: {}  // 存储各群组的在线用户数 {groupId: count}
  },
  getters: {},
  mutations: {
    loadCurrentUser(state, user) {
      state.currentUser = user;
      localStorage.setItem("currentUser", JSON.stringify(user));
    },
    loadSysConfig(state, sysConfig) {
      state.sysConfig = sysConfig;
      localStorage.setItem("sysConfig", JSON.stringify(sysConfig));
    },
    updateOnlineUserCount(state, {groupId, count}) {
      state.onlineUserCount[groupId] = count;
    }
  },
  actions: {},
  modules: {},
  plugins: []
})
