import { getToken, setToken, removeToken } from '@/utils/auth'
import { login, logout, getInfo } from '@/api/user'

const getDefaultState = () => ({
  token: getToken(),
  name: '',
  avatar: '',
  menus: []
})

const state = getDefaultState()

const mutations = {
  RESET_STATE: (state) => {
    Object.assign(state, getDefaultState())
  },
  SET_TOKEN: (state, token) => {
    state.token = token
  },
  SET_NAME: (state, name) => {
    state.name = name
  },
  SET_AVATAR: (state, avatar) => {
    state.avatar = avatar
  },
  SET_MENUS: (state, menus) => {
    state.menus = menus
  }
}

const actions = {
  login({ commit }, userInfo) {
    const { username, password } = userInfo
    return login({ username: username.trim(), password }).then(response => {
      const { data } = response
      commit('SET_TOKEN', data.token)
      setToken(data.token)
    })
  },

  getInfo({ commit }) {
    return getInfo().then(response => {
      const { data } = response
      if (!data) {
        return Promise.reject('验证失败，请重新登录')
      }
      commit('SET_NAME', data.name)
      commit('SET_AVATAR', data.avatar || '')
      commit('SET_MENUS', data.routers || [])
      return data
    })
  },

  logout({ commit }) {
    return logout().then(() => {
      removeToken()
      commit('RESET_STATE')
    })
  },

  resetToken({ commit }) {
    removeToken()
    commit('RESET_STATE')
    return Promise.resolve()
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
