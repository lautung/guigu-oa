import eslint from '@eslint/js'
import eslintConfigPrettier from 'eslint-config-prettier'
import eslintPluginVue from 'eslint-plugin-vue'
import globals from 'globals'
import typescriptEslint from 'typescript-eslint'

export default typescriptEslint.config(
  { ignores: ['dist/**', 'node_modules/**', 'coverage/**'] },
  eslint.configs.recommended,
  ...typescriptEslint.configs.recommended,
  ...eslintPluginVue.configs['flat/recommended'],
  {
    files: ['src/**/*.{ts,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: globals.browser,
      parserOptions: {
        parser: typescriptEslint.parser,
      },
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'error',
      // 审批表单值只按文本呈现（#142 / ADR 0028）：v-html 会把存储的用户输入注入 DOM。
      'vue/no-v-html': 'error',
    },
  },
  {
    // 公告正文是唯一允许 v-html 的渲染面（ADR 0028/0055）：服务端写入时按同源白名单
    // 消毒，客户端只注入已消毒 HTML。例外只开这一个文件，`npm run check:v-html`
    // 守卫没有第二处。
    files: ['src/notice/NoticeContent.vue'],
    rules: {
      'vue/no-v-html': 'off',
    },
  },
  {
    files: ['vite.config.js', 'scripts/**/*.mjs'],
    languageOptions: {
      globals: globals.node,
    },
  },
  {
    files: [
      'playwright.config.ts',
      'playwright.api.config.ts',
      'tests/e2e/**/*.ts',
      'tests/e2e-stack/**/*.ts',
      'tests/api/**/*.ts',
    ],
    languageOptions: {
      globals: globals.node,
    },
  },
  {
    files: ['tests/unit/**/*.{js,ts}'],
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node,
      },
    },
  },
  eslintConfigPrettier,
)
