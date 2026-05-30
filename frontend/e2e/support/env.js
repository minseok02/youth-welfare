import fs from "node:fs";
import path from "node:path";

const FRONTEND_DIR = process.cwd();
const ROOT_DIR = path.resolve(FRONTEND_DIR, "..");
const ADMIN_EMAIL_FILE = "/tmp/youth-welfare-admin-smoke-email";
const ADMIN_PASSWORD_FILE = "/tmp/youth-welfare-admin-smoke-password";

function trim(value) {
  return typeof value === "string" ? value.trim() : "";
}

function unquote(value) {
  if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
    return value.slice(1, -1);
  }
  return value;
}

function readEnvFile(filePath) {
  if (!fs.existsSync(filePath)) {
    return {};
  }

  const entries = {};
  for (const rawLine of fs.readFileSync(filePath, "utf8").split(/\r?\n/)) {
    const line = trim(rawLine);
    if (!line || line.startsWith("#") || !line.includes("=")) {
      continue;
    }

    const separatorIndex = line.indexOf("=");
    let key = trim(line.slice(0, separatorIndex));
    let value = unquote(line.slice(separatorIndex + 1));
    if (key.startsWith("export ")) {
      key = trim(key.slice(7));
    }

    if (!key) {
      continue;
    }

    entries[key] = value;
  }

  return entries;
}

function resolveEnvFilePath(rawPath) {
  const candidate = trim(rawPath);
  if (!candidate) {
    return path.join(ROOT_DIR, ".env");
  }

  if (path.isAbsolute(candidate)) {
    return candidate;
  }

  if (fs.existsSync(candidate)) {
    return path.resolve(FRONTEND_DIR, candidate);
  }

  return path.resolve(ROOT_DIR, candidate);
}

function firstCsvValue(value) {
  return trim((value || "").split(",")[0]);
}

function readFirstLine(filePath) {
  if (!fs.existsSync(filePath)) {
    return "";
  }

  const [firstLine = ""] = fs.readFileSync(filePath, "utf8").split(/\r?\n/);
  return trim(firstLine);
}

const envFile = readEnvFile(resolveEnvFilePath(process.env.ENV_FILE));

export function resolveAdminCredentials() {
  const email = trim(
    process.env.E2E_ADMIN_EMAIL
    || process.env.ADMIN_EMAIL
    || readFirstLine(ADMIN_EMAIL_FILE)
    || envFile.ADMIN_EMAIL
    || firstCsvValue(process.env.SECURITY_ADMIN_EMAILS || envFile.SECURITY_ADMIN_EMAILS)
    || "admin@example.com"
  );
  const password = trim(
    process.env.E2E_ADMIN_PASSWORD
    || process.env.ADMIN_PASSWORD
    || readFirstLine(ADMIN_PASSWORD_FILE)
    || envFile.ADMIN_PASSWORD
    || "password123!"
  );

  return { email, password };
}

export function resolveUserCredentials() {
  const email = trim(
    process.env.E2E_USER_EMAIL
    || process.env.SMOKE_EMAIL
    || envFile.SMOKE_EMAIL
    || "recommend.cl.af8b89b472bd4b@example.com"
  );
  const password = trim(
    process.env.E2E_USER_PASSWORD
    || process.env.SMOKE_PASSWORD
    || envFile.SMOKE_PASSWORD
    || "Password123!"
  );

  return { email, password };
}
