import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { authApi } from "../api/auth.js";

const PW_PATTERN = /^(?=.*[a-z])(?=.*\d)(?=.*[@$!%*?&])[a-z\d@$!%*?&]{6,20}$/;

export default function ResetConfirmPage() {
  const [searchParams] = useSearchParams();
  const [token, setToken] = useState(searchParams.get("token") || "");
  const [pw, setPw]           = useState("");
  const [confirm, setConfirm] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError]     = useState("");
  const navigate = useNavigate();

  const submit = async () => {
    if (!token) { setError("재설정 토큰을 입력해주세요 (이메일로 받은 링크에 포함되어 있습니다)."); return; }
    if (pw !== confirm) { setError("비밀번호가 일치하지 않습니다."); return; }
    if (!PW_PATTERN.test(pw)) {
      setError("비밀번호는 영문 소문자·숫자·특수문자(@$!%*?&)를 포함한 6~20자여야 합니다.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      await authApi.resetPasswordConfirm(token, pw);
      navigate("/login", { replace: true, state: { resetDone: true } });
    } catch (err) {
      setError(err.message || "비밀번호 재설정에 실패했습니다. 토큰이 만료되었을 수 있습니다.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={S.page}>
      <div style={S.left}>
        <span style={{ fontSize: 52 }}>🚇</span>
        <h1 style={S.brand}>새 비밀번호 설정</h1>
        <p style={S.tagline}>이메일로 받은 재설정 링크의 토큰과<br />새 비밀번호를 입력하세요.</p>
      </div>

      <div style={S.right}>
        <div style={S.box}>
          <h2 style={S.title}>비밀번호 재설정</h2>
          <p style={S.desc}>토큰은 30분간만 유효합니다.</p>

          <label style={S.label}>재설정 토큰</label>
          <input style={{ ...S.input, marginBottom: 14 }} type="text"
            placeholder="이메일 링크의 토큰 값"
            value={token} onChange={(e) => setToken(e.target.value)} />

          <label style={S.label}>새 비밀번호</label>
          <input style={{ ...S.input, marginBottom: 14 }} type="password"
            placeholder="영문 소문자·숫자·특수문자 포함 6~20자"
            value={pw} onChange={(e) => setPw(e.target.value)} />

          <label style={S.label}>새 비밀번호 확인</label>
          <input style={S.input} type="password"
            placeholder="비밀번호 재입력"
            value={confirm} onChange={(e) => setConfirm(e.target.value)} />

          {error && <p style={{ fontSize: 12, color: "#DC2626", marginTop: 10 }}>{error}</p>}

          <button style={{ ...S.btn, marginTop: 20, opacity: loading ? 0.7 : 1 }}
            onClick={submit} disabled={loading}>
            {loading ? "변경 중..." : "비밀번호 변경"}
          </button>

          <div style={{ textAlign: "center", marginTop: 16, fontSize: 13 }}>
            <Link to="/login" style={S.link}>로그인으로 돌아가기</Link>
          </div>
        </div>
      </div>
    </div>
  );
}

const S = {
  page: { display: "flex", minHeight: "100vh", background: "linear-gradient(135deg, #0D3B7A 0%, #1565C0 100%)" },
  left: { flex: 1, display: "flex", flexDirection: "column", justifyContent: "center", padding: "60px 56px", color: "#fff" },
  brand: { fontSize: 32, fontWeight: 700, lineHeight: 1.3, margin: "20px 0 16px" },
  tagline: { fontSize: 15, color: "rgba(255,255,255,0.75)", lineHeight: 1.8 },
  right: { width: 480, background: "#fff", display: "flex", alignItems: "center", justifyContent: "center", padding: "40px 32px" },
  box: { width: "100%", maxWidth: 380 },
  title: { fontSize: 24, fontWeight: 700, color: "#111827", marginBottom: 6 },
  desc: { fontSize: 13, color: "#6B7280", marginBottom: 28 },
  label: { display: "block", fontSize: 12, fontWeight: 500, color: "#374151", marginBottom: 5 },
  input: { width: "100%", height: 44, padding: "0 14px", border: "1.5px solid #D1D5DB", borderRadius: 8, fontSize: 13, fontFamily: "inherit", outline: "none", boxSizing: "border-box" },
  btn: { width: "100%", height: 46, background: "#1565C0", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: "pointer", fontFamily: "inherit" },
  link: { color: "#1976D2", cursor: "pointer", textDecoration: "underline" },
};
