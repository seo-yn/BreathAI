const BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

export class ApiError extends Error {
  constructor(message, status, data) {
    super(message || "요청 처리 중 오류가 발생했습니다.");
    this.name = "ApiError";
    this.status = status;
    this.data = data;
  }
}

export function getStoredTokens() {
  return {
    accessToken: localStorage.getItem("token"),
    refreshToken: localStorage.getItem("refreshToken"),
  };
}

function storeTokens({ accessToken, refreshToken }) {
  if (accessToken) localStorage.setItem("token", accessToken);
  if (refreshToken) localStorage.setItem("refreshToken", refreshToken);
}

export function clearTokens() {
  localStorage.removeItem("token");
  localStorage.removeItem("refreshToken");
}

// AuthContext가 mount 시 등록 — 재발급까지 실패하면 로그인 화면으로 보낸다.
let onAuthExpired = null;
export function setAuthExpiredHandler(fn) {
  onAuthExpired = fn;
}

// 이 백엔드는 만료/누락된 토큰에 대해 401(EXPIRED_TOKEN)을 거의 던지지 않는다 —
// JwtAuthenticationFilter가 만료 예외를 catch(Exception)으로 삼키고 인증 없이 요청을
// 통과시켜서, 실제로는 엔드포인트에 따라 403(빈 바디) 또는 404("사용자를 찾을 수 없습니다")로
// 응답한다. 상태코드로 "토큰 만료"와 "진짜 권한/존재 오류"를 구분할 수 없으므로,
// 응답을 보고 반응하는 대신 토큰 자체의 exp 클레임을 미리 읽어 만료 전에 선제 재발급한다.
function decodeJwtExpMs(token) {
  try {
    const payload = token.split(".")[1];
    const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
    const json = JSON.parse(atob(base64));
    return typeof json.exp === "number" ? json.exp * 1000 : null;
  } catch {
    return null;
  }
}

function isAccessTokenExpiringSoon() {
  const { accessToken } = getStoredTokens();
  if (!accessToken) return true;
  const expMs = decodeJwtExpMs(accessToken);
  if (expMs === null) return false; // exp를 못 읽으면 판단 보류 (요청 그대로 시도)
  return Date.now() >= expMs - 10_000; // 만료 10초 전부터 미리 갱신
}

async function rawRequest(method, path, body, accessToken, isFormData) {
  const headers = {};
  if (accessToken) headers["Authorization"] = `Bearer ${accessToken}`;
  if (!isFormData) headers["Content-Type"] = "application/json";

  let res;
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: isFormData ? body : body ? JSON.stringify(body) : null,
    });
  } catch {
    throw new ApiError("서버에 연결할 수 없습니다.", 0, null);
  }

  try {
    return await res.json();
  } catch {
    throw new ApiError("서버 응답을 처리할 수 없습니다.", res.status, null);
  }
}

// 동시에 여러 401이 터져도 재발급 요청은 한 번만 나가도록 공유
let refreshPromise = null;
async function reissueTokens() {
  const { refreshToken } = getStoredTokens();
  if (!refreshToken) throw new ApiError("로그인이 필요합니다.", 401, null);

  if (!refreshPromise) {
    refreshPromise = rawRequest("POST", "/api/v1/auth/reissue", { refreshToken }, null, false)
      .then((json) => {
        if (!json.success) throw new ApiError(json.message, json.status, json.data);
        storeTokens(json.data);
        return json.data;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

/**
 * @param {string} method
 * @param {string} path
 * @param {{body?: any, isFormData?: boolean, skipAuthRetry?: boolean}} [opts]
 * skipAuthRetry: 토큰 만료/재발급 로직을 아예 건너뜀 (로그인/회원가입/재발급 자체 호출용)
 */
export async function request(method, path, opts = {}) {
  const { body, isFormData, skipAuthRetry } = opts;

  // 요청 전에 만료 임박 여부를 먼저 확인해 선제적으로 재발급 (위 설명 참고)
  if (!skipAuthRetry && isAccessTokenExpiringSoon() && getStoredTokens().refreshToken) {
    try {
      await reissueTokens();
    } catch {
      clearTokens();
      onAuthExpired?.();
      throw new ApiError("로그인이 만료되었습니다. 다시 로그인해주세요.", 401, null);
    }
  }

  const { accessToken } = getStoredTokens();
  const json = await rawRequest(method, path, body, accessToken, isFormData);

  if (json.success) return json;

  // 선제 갱신을 놓친 극히 드문 경우(시계 오차 등)의 최후 방어선 — 진짜 401일 때만 1회 재시도
  if (json.status === 401 && !skipAuthRetry) {
    try {
      const { accessToken: newToken } = await reissueTokens();
      const retryJson = await rawRequest(method, path, body, newToken, isFormData);
      if (retryJson.success) return retryJson;
      throw new ApiError(retryJson.message, retryJson.status, retryJson.data);
    } catch (err) {
      clearTokens();
      onAuthExpired?.();
      throw err instanceof ApiError ? err : new ApiError("인증이 만료되었습니다.", 401, null);
    }
  }

  throw new ApiError(json.message, json.status, json.data);
}

export { BASE_URL };
