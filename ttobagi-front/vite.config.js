import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 기본값(localhost)은 macOS에서 IPv6 ::1 에만 바인딩돼, 브라우저가 localhost를
    // 127.0.0.1로 해석하면 연결이 거부된다. 0.0.0.0으로 열어 IPv4에서도 접속되게 한다.
    // 접속은 그대로 http://localhost:5173 을 쓴다 — 백엔드 CORS가 그 오리진만 허용한다.
    host: true,
  },
})
