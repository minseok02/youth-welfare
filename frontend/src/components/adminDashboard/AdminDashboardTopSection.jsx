import {
  AdminDashboardTopHeader,
} from "./AdminDashboardTopPanels";

export default function AdminDashboardTopSection({
  windowDays,
  onWindowDaysChange,
  isRefreshing,
  onRefreshAll,
}) {
  return (
    <AdminDashboardTopHeader
      windowDays={windowDays}
      onWindowDaysChange={onWindowDaysChange}
      isRefreshing={isRefreshing}
      onRefreshAll={onRefreshAll}
    />
  );
}
