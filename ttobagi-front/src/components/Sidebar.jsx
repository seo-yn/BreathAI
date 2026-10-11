import { NavLink } from "react-router-dom";

const NAV = [
  { to: "/dashboard", icon: "📊", label: "대시보드" },
  { to: "/analyze",   icon: "🔍", label: "신규 데이터 분석" },
  { to: "/faq",       icon: "📋", label: "FAQ 후보 관리" },
  { to: "/training-data", icon: "📚", label: "기존 학습데이터" },
  { to: "/faq/versions", icon: "🗂️", label: "FAQ 버전 관리" },
  { to: "/account",   icon: "👤", label: "계정 관리" },
];

export default function Sidebar() {
  return (
    <div style={S.sidebar}>
      <div style={S.logo}>
        <span style={{ fontSize: 24 }}>🚇</span>
        <div>
          <div style={S.logoText}>또타24</div>
          <div style={S.logoSub}>현행화 시스템</div>
        </div>
      </div>

      <nav style={{ flex: 1 }}>
        <div style={S.section}>메뉴</div>
        {NAV.map((n) => (
          <NavLink key={n.to} to={n.to}
            style={({ isActive }) => ({ ...S.navItem, ...(isActive ? S.navActive : {}) })}>
            <span>{n.icon}</span>
            <span>{n.label}</span>
          </NavLink>
        ))}
      </nav>

      <div style={S.footer}>BreathAI · v1.0</div>
    </div>
  );
}

const S = {
  sidebar:     { width: 220, minHeight: "100vh", background: "#0D3B7A", display: "flex", flexDirection: "column", position: "fixed", left: 0, top: 0, bottom: 0, zIndex: 100 },
  logo:        { display: "flex", alignItems: "center", gap: 10, padding: "18px 20px", borderBottom: "1px solid rgba(255,255,255,.1)" },
  logoText:    { fontSize: 14, fontWeight: 700, color: "#fff" },
  logoSub:     { fontSize: 10, color: "rgba(255,255,255,.5)" },
  section:     { fontSize: 10, fontWeight: 600, color: "rgba(255,255,255,.4)", letterSpacing: ".08em", textTransform: "uppercase", padding: "16px 20px 6px" },
  navItem:     { display: "flex", alignItems: "center", gap: 10, padding: "10px 20px", fontSize: 13, color: "rgba(255,255,255,.7)", cursor: "pointer", borderLeft: "3px solid transparent", transition: "all .15s", textDecoration: "none" },
  navActive:   { background: "rgba(255,255,255,.1)", color: "#fff", borderLeftColor: "#64B5F6" },
  footer:      { padding: "14px 20px", borderTop: "1px solid rgba(255,255,255,.1)", fontSize: 11, color: "rgba(255,255,255,.3)" },
};
