import { useState } from "react";
import { authApi } from "../api/auth.js";
import { useAuth } from "../hooks/useAuth.js";
import { useToast } from "../hooks/useToast.js";

// 참고: 백엔드 /promote는 @AuthenticationPrincipal(현재 로그인한 사용자)만 승격시키고
// 요청 바디의 email은 사용하지 않는다 — 그래서 "내 계정 승격" UX로 구성한다.
export default function AdminPromotePage() {
  const { user, setUser } = useAuth();
  const [adminCode, setAdminCode] = useState("");
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState("");
  const toast = useToast();

  const submit = async () => {
    if (!adminCode) { setError("관리자 인증 코드를 입력해주세요."); return; }
    setLoading(true);
    setError("");
    try {
      const res = await authApi.promote(user?.email, adminCode);
      setUser(res.data);
      setAdminCode("");
      toast.success("관리자 권한이 부여되었습니다.");
    } catch (err) {
      setError(err.message || "권한 부여에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  if (user?.role === "ADMIN") {
    return (
      <div style={S.page}>
        <div style={S.card}>
          <div style={{ fontSize: 14, color: "#111827" }}>이미 관리자 권한을 보유하고 있습니다.</div>
        </div>
      </div>
    );
  }

  return (
    <div style={S.page}>
      <div style={S.card}>
        <div style={S.title}>관리자 권한 부여</div>
        <div style={S.desc}>현재 계정({user?.email})에 관리자 인증 코드를 입력하면 즉시 ADMIN 권한이 부여됩니다.</div>

        <input style={S.input} type="password" placeholder="관리자 인증 코드"
          value={adminCode} onChange={(e) => setAdminCode(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && submit()} />

        {error && <div style={S.error}>{error}</div>}

        <button style={{ ...S.btn, opacity: loading ? 0.7 : 1 }} onClick={submit} disabled={loading}>
          {loading ? "처리 중..." : "권한 부여"}
        </button>
      </div>
    </div>
  );
}

const S = {
  page:  { padding: 28, display: "flex", justifyContent: "center" },
  card:  { background: "#fff", borderRadius: 12, boxShadow: "0 1px 3px rgba(0,0,0,.08)", border: "1px solid rgba(0,0,0,.06)", padding: 28, maxWidth: 420, width: "100%" },
  title: { fontSize: 18, fontWeight: 700, color: "#111827", marginBottom: 6 },
  desc:  { fontSize: 13, color: "#6B7280", marginBottom: 20, lineHeight: 1.6 },
  input: { width: "100%", height: 44, padding: "0 14px", border: "1.5px solid #D1D5DB", borderRadius: 8, fontSize: 13, fontFamily: "inherit", outline: "none", boxSizing: "border-box", marginBottom: 12 },
  error: { fontSize: 12, color: "#DC2626", marginBottom: 12 },
  btn:   { width: "100%", height: 46, background: "#1565C0", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: "pointer", fontFamily: "inherit" },
};
