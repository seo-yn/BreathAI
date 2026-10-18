import { useCallback, useRef, useState } from "react";
import { ToastContext } from "./toast-context.js";

let idCounter = 0;

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const timers = useRef({});

  const dismiss = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
    clearTimeout(timers.current[id]);
    delete timers.current[id];
  }, []);

  const showToast = useCallback((type, message, duration = 3500) => {
    const id = ++idCounter;
    setToasts((prev) => [...prev, { id, type, message }]);
    timers.current[id] = setTimeout(() => dismiss(id), duration);
    return id;
  }, [dismiss]);

  const toast = {
    success: (message) => showToast("success", message),
    error: (message) => showToast("error", message),
    info: (message) => showToast("info", message),
  };

  return (
    <ToastContext.Provider value={toast}>
      {children}
      <div style={S.stack}>
        {toasts.map((t) => (
          <div key={t.id} style={{ ...S.toast, ...S[t.type] }} onClick={() => dismiss(t.id)}>
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

const S = {
  stack: {
    position: "fixed", top: 20, right: 20, zIndex: 2000,
    display: "flex", flexDirection: "column", gap: 8, maxWidth: 360,
  },
  toast: {
    padding: "12px 16px", borderRadius: 10, fontSize: 13, fontWeight: 500,
    color: "#fff", boxShadow: "0 8px 24px rgba(0,0,0,.15)", cursor: "pointer",
    lineHeight: 1.5, wordBreak: "keep-all",
  },
  success: { background: "#1B8E4D" },
  error:   { background: "#D93025" },
  info:    { background: "#1565C0" },
};
