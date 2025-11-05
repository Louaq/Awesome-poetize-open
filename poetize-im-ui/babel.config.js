module.exports = {
  presets: [
    ['@vue/cli-plugin-babel/preset', {
      targets: {
        node: 'current'
      }
    }]
  ],
  plugins: [
    '@babel/plugin-proposal-optional-chaining',
    '@babel/plugin-proposal-nullish-coalescing-operator'
  ],
  env: {
    production: {
      plugins: [
        // ⚠️ 重要：生产环境日志处理
        // 自动移除所有 console.log/debug/info 调用，只保留 error 和 warn
        // 这样开发时可以随意使用 console.log，生产环境零性能开销
        // 不需要额外的 logger 包装器！直接用原生 console 即可
        ['transform-remove-console', { exclude: ['error', 'warn'] }]
      ]
    }
  }
}
