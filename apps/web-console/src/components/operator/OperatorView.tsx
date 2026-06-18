import { useQuery } from "@tanstack/react-query";
import { fetchDashboard } from "../../api";
import { useObjectWebSocket } from "../../hooks/useObjectWebSocket";
import DashboardBuilder from "../dashboard/DashboardBuilder";
import OperatorSidebar from "./OperatorSidebar";

const DEFAULT_DASHBOARD = "root.platform.dashboards.demo-sensor";

interface OperatorViewProps {
  dashboardPath?: string;
  operatorId?: string;
  onSwitchAdmin?: () => void;
}

export default function OperatorView({
  dashboardPath = DEFAULT_DASHBOARD,
  operatorId = "operator",
  onSwitchAdmin,
}: OperatorViewProps) {
  useObjectWebSocket();

  const dashboard = useQuery({
    queryKey: ["dashboard", dashboardPath],
    queryFn: () => fetchDashboard(dashboardPath),
  });

  return (
    <div className="operator-shell">
      <header className="operator-topbar">
        <div>
          <strong>Оператор · HMI</strong>
          <span className="brand-sub">{dashboard.data?.title ?? dashboardPath}</span>
        </div>
        {onSwitchAdmin && (
          <button type="button" className="btn" onClick={onSwitchAdmin}>
            Админ-консоль
          </button>
        )}
      </header>
      <div className="operator-layout">
        <main className="operator-dashboard">
          <DashboardBuilder
            path={dashboardPath}
            operatorMode
            onClose={() => undefined}
          />
        </main>
        <aside className="operator-sidebar">
          <OperatorSidebar operatorId={operatorId} />
        </aside>
      </div>
    </div>
  );
}
