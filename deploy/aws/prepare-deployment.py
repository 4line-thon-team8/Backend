"""Create runtime files from Actions secrets without printing their values."""
import ipaddress
import os
from pathlib import Path
import re
import shutil


def required(name):
    value = os.environ.get(name, "")
    if not value or any(ord(c) < 32 for c in value):
        raise SystemExit(f"Missing or invalid setting: {name}")
    return value


def pattern(name, expression):
    value = required(name)
    if not re.fullmatch(expression, value):
        raise SystemExit(f"Invalid format: {name}")
    return value


os.umask(0o077)
out = Path(os.environ["RUNNER_TEMP"]) / "payload"
out.mkdir(exist_ok=True)
sha = pattern("GITHUB_SHA", r"[0-9a-f]{40}")
domain = pattern("API_DOMAIN", r"[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\.[a-z]{2,}")
host = pattern("RDS_ENDPOINT", r"[a-zA-Z0-9.-]+")
database = pattern("RDS_DATABASE", r"[a-zA-Z][a-zA-Z0-9_]*")
username = pattern("RDS_USERNAME", r"[a-zA-Z][a-zA-Z0-9_]*")
password = required("RDS_PASSWORD")
if not ipaddress.IPv4Address(required("EC2_HOST")).is_global:
    raise SystemExit("EC2_HOST must be a public IPv4 address")
pattern("EC2_USER", r"[a-z_][a-z0-9_-]*")
ai = os.environ.get("ENABLE_AI", "false").lower()
if ai not in ("true", "false"):
    raise SystemExit("Invalid ENABLE_AI")
api_key = required("OPENAI_API_KEY") if ai == "true" else ""
(out / ".env").write_text(f"APP_IMAGE=fivegrade-backend:{sha}\nAPI_DOMAIN={domain}\nDB_HOST={host}\nDB_NAME={database}\n")
values = {
    "HOLISTIC_ENABLED": "true",
    "DB_USER": username, "DB_PASSWORD": password,
    "CORS_ALLOWED_ORIGINS": "https://odeunggeup-app.vercel.app",
    "OPENAPI_ENABLED": "false", "REPORT_AI_ENABLED": ai,
    "REPORT_AI_MODEL": "gpt-4.1-mini-2025-04-14",
    "REPORT_AI_TIMEOUT_MS": "12000", "REPORT_AI_MAX_CONCURRENT": "4",
    "HOLISTIC_REPORT_AI_ENABLED": ai,
    "HOLISTIC_REPORT_AI_MODEL": "gpt-4.1-mini-2025-04-14",
    "HOLISTIC_REPORT_AI_TIMEOUT_MS": "20000", "HOLISTIC_REPORT_AI_MAX_CONCURRENT": "4",
    "OPENAI_API_KEY": api_key,
}
(out / "runtime.env").write_text("".join(f"{key}={value}\n" for key, value in values.items()))
root = Path(__file__).resolve().parent
for name in ("compose.aws.yaml", "Caddyfile", "deploy-remote.sh"):
    shutil.copyfile(root / name, out / name)
key = os.environ.get("EC2_SSH_PRIVATE_KEY", "")
known = os.environ.get("EC2_KNOWN_HOSTS", "")
if not key.strip() or not known.strip():
    raise SystemExit("EC2_SSH_PRIVATE_KEY and EC2_KNOWN_HOSTS are required")
(Path(os.environ["RUNNER_TEMP"]) / "deploy_key").write_text(key.rstrip() + "\n")
(Path(os.environ["RUNNER_TEMP"]) / "known_hosts").write_text(known.rstrip() + "\n")
print("Deployment files prepared (values hidden).")
