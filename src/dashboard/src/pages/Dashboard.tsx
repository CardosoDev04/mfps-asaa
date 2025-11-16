import AssemblyDashboard from "./AssemblyDashboard";
import CommunicationDashboard from "./CommunicationDashboard";
import TransportDashboard from "./TransportDashboard";

export default function Dashboard() {
  return (
    <div className="w-full h-full bg-slate-50 overflow-auto">
      <div className="max-w-7xl mx-auto flex flex-col gap-6 p-4">
        <h1 className="text-2xl font-bold mb-2">System Dashboard</h1>

        <div className="grid grid-cols-1 xl:grid-cols-2 gap-6 items-start">
          <AssemblyDashboard />
          <CommunicationDashboard />
          <TransportDashboard />
        </div>
      </div>
    </div>
  );
}
