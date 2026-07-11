import { useState } from "react";

export function useAdminDashboardFilters() {
  const [windowDays, setWindowDays] = useState(14);
  const [selectedCodeSetKey, setSelectedCodeSetKey] = useState("");
  const [codebookQueryText, setCodebookQueryText] = useState("");
  const [policyErrorStatusFilter, setPolicyErrorStatusFilter] = useState("OPEN");
  const [supportInquiryStatusFilter, setSupportInquiryStatusFilter] = useState("OPEN");
  const [policyDuplicateStatusFilter, setPolicyDuplicateStatusFilter] = useState("OPEN");
  const [policyLinkStatusFilter, setPolicyLinkStatusFilter] = useState("OPEN");
  const [notificationStaleDays, setNotificationStaleDays] = useState(14);

  return {
    windowDays,
    setWindowDays,
    selectedCodeSetKey,
    setSelectedCodeSetKey,
    codebookQueryText,
    setCodebookQueryText,
    policyErrorStatusFilter,
    setPolicyErrorStatusFilter,
    supportInquiryStatusFilter,
    setSupportInquiryStatusFilter,
    policyDuplicateStatusFilter,
    setPolicyDuplicateStatusFilter,
    policyLinkStatusFilter,
    setPolicyLinkStatusFilter,
    notificationStaleDays,
    setNotificationStaleDays,
  };
}
