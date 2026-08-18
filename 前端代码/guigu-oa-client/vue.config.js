'use strict'
const path = require('path')

function resolve(dir) {
  return path.join(__dirname, dir)
}

const port = process.env.port || process.env.npm_config_port || 9530

module.exports = {
  publicPath: '/',
  outputDir: 'dist',
  assetsDir: 'static',
  lintOnSave: false,
  productionSourceMap: false,
  devServer: {
    port,
    open: true,
    proxy: {
      '/dev-api': {
        target: 'http://localhost:8800',
        changeOrigin: true,
        pathRewrite: {
          '^/dev-api': ''
        }
      }
    }
  },
  configureWebpack: {
    name: '云尚办公',
    resolve: {
      alias: {
        '@': resolve('src')
      }
    }
  }
}
