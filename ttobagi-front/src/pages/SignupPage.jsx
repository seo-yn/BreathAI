import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { authApi } from "../api/auth.js";
import { useToast } from "../hooks/useToast.js";

const PW_PATTERN = /^(?=.*[a-z])(?=.*\d)(?=.*[@$!%*?&])[a-z\d@$!%*?&]{6,20}$/;

export default function SignupPage() {
  const [email, setEmail]         = useState("");
  const [pw, setPw]               = useState("");
  const [confirm, setConfirm]     = useState("");
  const [adminCode, setAdminCode] = useState("");
  const [loading, setLoading]     = useState(false);
  const [error, setError]         = useState("");
  const toast = useToast();
  const navigate = useNavigate();

  const submit = async () => {
    if (!email || !pw) { setError("모든 항목을 입력해주세요."); return; }
    if (pw !== confirm) { setError("비밀번호가 일치하지 않습니다."); return; }
    if (!PW_PATTERN.test(pw)) {
      setError("비밀번호는 영문 소문자·숫자·특수문자(@$!%*?&)를 포함한 6~20자여야 합니다.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      await authApi.signup(email, pw, adminCode || undefined);
      toast.success("가입 완료! 로그인 해주세요.");
      navigate("/login", { replace: true });
    } catch (err) {
      setError(err.message || "회원가입에 실패했습니다.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={S.page}>
      <div style={S.left}>
        <span style={{ fontSize: 52 }}>🚇</span>
        <h1 style={S.brand}>또타24<br />현행화 시스템</h1>
        <p style={S.tagline}>서울교통공사 운영 담당자를 위한<br />챗봇 학습데이터 관리 시스템</p>
      </div>

      <div style={S.right}>
        <div style={S.box}>
          <h2 style={S.title}>회원가입</h2>
          <p style={S.desc}>운영자 계정을 생성합니다.</p>

          {[
            { label: "이메일", type: "email", ph: "admin@seoulmetro.co.kr", val: email, set: setEmail },
            { label: "비밀번호 (영문 소문자·숫자·특수문자 포함 6~20자)", type: "password", ph: "예: abc123!@", val: pw, set: setPw },
            { label: "비밀번호 확인", type: "password", ph: "비밀번호 재입력", val: confirm, set: setConfirm },
          ].map((f) => (
            <div key={f.label} style={{ marginBottom: 14 }}>
              <label style={S.label}>{f.label}</label>
              <input style={S.input} type={f.type} placeholder={f.ph}
                value={f.val} onChange={(e) => f.set(e.target.value)} />
            </div>
          ))}

          <div style={{ marginBottom: 14 }}>
            <label style={S.label}>관리자 코드 (선택 — 있으면 즉시 관리자로 가입됩니다)</label>
            <input style={S.input} type="text" placeholder="관리자가 아니면 비워두세요"
              value={adminCode} onChange={(e) => setAdminCode(e.target.value)} />
          </div>

          {error && <p style={{ fontSize: 12, color: "#DC2626", marginTop: 4 }}>{error}</p>}

          <button style={{ ...S.btn, marginTop: 10, opacity: loading ? 0.7 : 1 }}
            onClick={submit} disabled={loading}>
            {loading ? "처리 중..." : "가입하기"}
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
  desc: { fontSize: 13, color: "#6B7280", marginBottom: 24 },
  label: { display: "block", fontSize: 12, fontWeight: 500, color: "#374151", marginBottom: 5 },
  input: { width: "100%", height: 44, padding: "0 14px", border: "1.5px solid #D1D5DB", borderRadius: 8, fontSize: 13, fontFamily: "inherit", outline: "none", boxSizing: "border-box" },
  btn: { width: "100%", height: 46, background: "#1565C0", color: "#fff", border: "none", borderRadius: 8, fontSize: 14, fontWeight: 600, cursor: "pointer", fontFamily: "inherit" },
  link: { color: "#1976D2", cursor: "pointer", textDecoration: "underline" },
};
