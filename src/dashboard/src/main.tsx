import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./index.css";
import AssemblyDashboard from "./pages/dashboard/AssemblyDashboard";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AssemblyDashboard />
  </StrictMode>
);
