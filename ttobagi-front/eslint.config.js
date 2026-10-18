import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import { defineConfig, globalIgnores } from 'eslint/config'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{js,jsx}'],
    extends: [
      js.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
  },
  {
    // 설정 파일은 브라우저가 아니라 Node에서 실행된다.
    // vite.config.js가 process.env(VITE_BASE_PATH)를 읽으므로 Node 전역을 허용한다.
    files: ['*.config.js', 'vite.config.js', 'eslint.config.js'],
    languageOptions: { globals: globals.node },
  },
])
