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
        ['transform-remove-console', { exclude: ['error', 'warn'] }]
      ]
    }
  }
}
