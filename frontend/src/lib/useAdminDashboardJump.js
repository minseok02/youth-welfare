import { SECTION_FLASH_TONE } from "../components/adminDashboard/AdminDashboardUiTokens";
import {
  ADMIN_DASHBOARD_DEFAULT_JUMP_PRESET,
  ADMIN_DASHBOARD_FOCUS_KEYS,
  ADMIN_DASHBOARD_JUMP_PRESETS,
  ADMIN_DASHBOARD_TEST_ATTRS,
} from "./adminDashboardTestHooks";

function animateJumpTarget(target, tone, duration = 1400) {
  if (!target || typeof target.animate !== "function") {
    return;
  }
  const flashTone = SECTION_FLASH_TONE[tone] ?? SECTION_FLASH_TONE.info;
  target.animate(
    [
      { boxShadow: "0 0 0 0 rgba(0,0,0,0)", backgroundColor: "rgba(0,0,0,0)" },
      { boxShadow: `0 0 0 4px ${flashTone.shadow}`, backgroundColor: flashTone.background },
      { boxShadow: "0 0 0 0 rgba(0,0,0,0)", backgroundColor: "rgba(0,0,0,0)" },
    ],
    { duration, easing: "ease-out" },
  );
}

function markJumpActive(target, tone = "info", duration) {
  if (!target) {
    return;
  }
  if (target.__jumpActiveTimeoutId) {
    window.clearTimeout(target.__jumpActiveTimeoutId);
  }
  target.setAttribute(ADMIN_DASHBOARD_TEST_ATTRS.jumpActive, "true");
  target.setAttribute(ADMIN_DASHBOARD_TEST_ATTRS.jumpActiveTone, tone);
  target.__jumpActiveTimeoutId = window.setTimeout(() => {
    target.removeAttribute(ADMIN_DASHBOARD_TEST_ATTRS.jumpActive);
    target.removeAttribute(ADMIN_DASHBOARD_TEST_ATTRS.jumpActiveTone);
    delete target.__jumpActiveTimeoutId;
  }, duration);
}

export function useAdminDashboardJump() {
  return (targetId, options = {}) => {
    const tone = options.tone ?? "info";
    const focusKey = options.focusKey ?? ADMIN_DASHBOARD_FOCUS_KEYS.default;
    const preset = ADMIN_DASHBOARD_JUMP_PRESETS[options.preset] ?? ADMIN_DASHBOARD_JUMP_PRESETS[ADMIN_DASHBOARD_DEFAULT_JUMP_PRESET];
    const section = document.getElementById(targetId);
    if (!section) {
      return;
    }
    section.scrollIntoView({ behavior: "smooth", block: "start" });
    markJumpActive(section, tone, preset.sectionDuration);
    animateJumpTarget(section, tone, preset.sectionDuration);

    const focusTargetSelector = focusKey === "default"
      ? `[${ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget}="true"], [${ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget}="${ADMIN_DASHBOARD_FOCUS_KEYS.default}"]`
      : `[${ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget}="${focusKey}"]`;
    const focusTarget = section.querySelector(focusTargetSelector);
    if (focusTarget && focusTarget !== section) {
      const focusTone = focusTarget.getAttribute(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTone) || tone;
      const focusContainer = focusTarget.closest(
        `[${ADMIN_DASHBOARD_TEST_ATTRS.adminCardKey}], [${ADMIN_DASHBOARD_TEST_ATTRS.adminListKey}], [${ADMIN_DASHBOARD_TEST_ATTRS.attentionKey}]`,
      );
      if (focusContainer && focusContainer !== section && focusContainer !== focusTarget) {
        markJumpActive(focusContainer, focusTone, preset.containerDuration);
        animateJumpTarget(focusContainer, focusTone, preset.containerDuration);
      }
      markJumpActive(focusTarget, focusTone, preset.focusDuration);
      animateJumpTarget(focusTarget, focusTone, preset.focusDuration);
    }
  };
}
