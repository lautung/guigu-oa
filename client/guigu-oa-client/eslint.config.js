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
    },
  },
  {
    files: ['vite.config.js', 'scripts/**/*.mjs'],
    languageOptions: {
      globals: globals.node,
    },
  },
  {
    files: ['playwright.config.ts', 'tests/e2e/**/*.ts', 'tests/e2e-stack/**/*.ts'],
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
