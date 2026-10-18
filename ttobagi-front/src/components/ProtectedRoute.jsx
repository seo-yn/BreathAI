import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../hooks/useAuth.js";
import Sidebar from "./Sidebar.jsx";

export default function ProtectedRoute() {
  const { user, loading } = useAuth();

  if (loading) {
    return <div style={{ padding: 40, color: "#6B7280" }}>불러오는 중...</div>;
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  return (
    <div style={{ display: "flex" }}>
      <Sidebar />
      <div style={{ marginLeft: 220, flex: 1, minHeight: "100vh", background: "#F9FAFB" }}>
        <Outlet />
      </div>
    </div>
  );
}
