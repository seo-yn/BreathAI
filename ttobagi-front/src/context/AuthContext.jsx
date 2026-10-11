import { useEffect, useState, useCallback } from "react";
import { authApi } from "../api/auth.js";
import { clearTokens, setAuthExpiredHandler } from "../api/client.js";
import { AuthContext } from "./auth-context.js";

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(() => Boolean(localStorage.getItem("token")));

  const logout = useCallback(() => {
    clearTokens();
    setUser(null);
  }, []);

  useEffect(() => {
    // 자동 재발급까지 실패하면(리프레시 토큰도 만료) 강제 로그아웃
    setAuthExpiredHandler(logout);
  }, [logout]);

  useEffect(() => {
    if (!localStorage.getItem("token")) return;
    // 새로고침 시에도 로그인 상태 유지 — 저장된 토큰으로 세션 복원 시도
    authApi.getMe()
      .then((res) => setUser(res.data))
      .catch(() => clearTokens())
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (tokens) => {
    // 토큰을 먼저 저장해야 뒤이은 getMe() 요청에 Authorization 헤더가 실린다.
    // 순서가 바뀌면(토큰 저장 전에 getMe 호출) 인증 없이 요청이 나가
    // 백엔드가 "사용자를 찾을 수 없습니다"(404)를 반환한다.
    if (tokens?.accessToken) localStorage.setItem("token", tokens.accessToken);
    if (tokens?.refreshToken) localStorage.setItem("refreshToken", tokens.refreshToken);
    const res = await authApi.getMe();
    setUser(res.data);
    return res.data;
  }, []);

  return (
    <AuthContext.Provider value={{ user, setUser, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}
