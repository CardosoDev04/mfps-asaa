import { StrictMode, useState } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import AssemblyDashboard from "./pages/AssemblyDashboard";
import CommunicationDashboard from "./pages/CommunicationDashboard";

function App() {
  const [view, setView] = useState<"assembly" | "communication">("assembly");
  return (
    <div>
      <nav
        style={{
          display: "flex",
          gap: 12,
          padding: 12,
          borderBottom: "1px solid #e5e7eb",
        }}
      >
        <button
          onClick={() => setView("assembly")}
          disabled={view === "assembly"}
        >
          Assembly
        </button>
        <button
          onClick={() => setView("communication")}
          disabled={view === "communication"}
        >
          Communication
        </button>
      </nav>
      {view === "assembly" ? <AssemblyDashboard /> : <CommunicationDashboard />}
    </div>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
