import {
  ADMIN_DASHBOARD_TEST_ATTRS,
} from "../../src/lib/adminDashboardTestHooks.js";

export function attrSelector(attrName, value) {
  return `[${attrName}="${value}"]`;
}

export function section(sectionId) {
  return `#${sectionId}`;
}

export function activeSection(sectionId) {
  return `${section(sectionId)}[${ADMIN_DASHBOARD_TEST_ATTRS.jumpActive}="true"]`;
}

export function activeFocus(focusKey) {
  return `${attrSelector(ADMIN_DASHBOARD_TEST_ATTRS.jumpFocusTarget, focusKey)}[${ADMIN_DASHBOARD_TEST_ATTRS.jumpActive}="true"]`;
}

export function activePrimaryFocus() {
  return activeFocus("true");
}

export function activeFocusWithin(sectionId, focusKey) {
  return `${section(sectionId)} ${activeFocus(focusKey)}`;
}

export function activePrimaryFocusWithin(sectionId) {
  return `${section(sectionId)} ${activePrimaryFocus()}`;
}

export function activeCard(cardKey) {
  return `${attrSelector(ADMIN_DASHBOARD_TEST_ATTRS.adminCardKey, cardKey)}[${ADMIN_DASHBOARD_TEST_ATTRS.jumpActive}="true"]`;
}

export function adminAction(actionKey) {
  return attrSelector(ADMIN_DASHBOARD_TEST_ATTRS.adminActionKey, actionKey);
}

export function activeList(listKey) {
  return `${attrSelector(ADMIN_DASHBOARD_TEST_ATTRS.adminListKey, listKey)}[${ADMIN_DASHBOARD_TEST_ATTRS.jumpActive}="true"]`;
}

export function attentionItem(attentionKey) {
  return attrSelector(ADMIN_DASHBOARD_TEST_ATTRS.attentionKey, attentionKey);
}

export function attentionAction(attentionKey) {
  return attrSelector(ADMIN_DASHBOARD_TEST_ATTRS.attentionActionKey, attentionKey);
}

export function attentionPrimary(attentionKey) {
  return attrSelector(ADMIN_DASHBOARD_TEST_ATTRS.attentionPrimaryKey, attentionKey);
}

export function attentionPrimaryAction(attentionKey) {
  return attrSelector(ADMIN_DASHBOARD_TEST_ATTRS.attentionPrimaryActionKey, attentionKey);
}
