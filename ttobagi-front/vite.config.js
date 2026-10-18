import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],

  // GitHub Pages 프로젝트 사이트는 https://<계정>.github.io/<레포>/ 하위에 올라간다.
  // CI에서 VITE_BASE_PATH로 주입하고, 로컬 개발에서는 '/'를 그대로 쓴다.
  // 라우터 basename은 이 값을 import.meta.env.BASE_URL로 받아 자동으로 따라간다.
  base: process.env.VITE_BASE_PATH || '/',

  server: {
    // 기본값(localhost)은 macOS에서 IPv6 ::1 에만 바인딩돼, 브라우저가 localhost를
    // 127.0.0.1로 해석하면 연결이 거부된다. 0.0.0.0으로 열어 IPv4에서도 접속되게 한다.
    // 접속은 그대로 http://localhost:5173 을 쓴다 — 백엔드 CORS가 그 오리진만 허용한다.
    host: true,
  },

  build: {
    // recharts가 번들의 대부분이라 별도 청크로 분리해 초기 로딩을 줄인다.
    // Vite 8(rolldown)은 manualChunks를 함수로만 받는다.
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/recharts')) return 'charts';
          if (id.includes('node_modules/react')) return 'react';
          return undefined;
        },
      },
    },
  },
})
