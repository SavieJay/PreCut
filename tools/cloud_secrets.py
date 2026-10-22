#!/usr/bin/env python3
"""Fills supabase/.env.secrets for the `cloud` Edge Function without ever printing a value.

- Creates the file from supabase/.env.secrets.example if it's missing (owner-only permissions).
- --service-account KEY.json: writes GOOGLE_SERVICE_ACCOUNT_JSON as one line of JSON.
- --elevenlabs-key-file FILE: writes ELEVENLABS_API_KEY from a file holding just the key, so the
  key never appears in a command line.
- Generates INSTALL_TOKEN_SECRET if it's empty (never replaces one already set: that would sign
  every install out).
- --apk APK (repeatable): adds the APKs' signing certificates (apksigner) to PLAY_CERT_SHA256.
- --cert SHA256 (repeatable): adds a certificate by its digest, e.g. Play Console's app signing key
  (Test and release → App integrity → App signing).

- --push: sends the values that are filled in to the linked Supabase project (`supabase secrets
  set`, through a temporary owner-only file that's deleted afterwards). Empty ones are left out,
  so a missing key never overwrites one already set.

The ElevenLabs key is pasted into the file by hand. Prints only which names are set.
Usage: python3 -I tools/cloud_secrets.py [--service-account KEY.json] [--apk APP.apk ...] [--cert SHA256 ...] [--push]
"""
import argparse
import glob
import json
import os
import re
import secrets
import subprocess
import sys
import tempfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SECRETS = os.path.join(ROOT, "supabase", ".env.secrets")
EXAMPLE = os.path.join(ROOT, "supabase", ".env.secrets.example")


def read_lines():
    if not os.path.exists(SECRETS):
        with open(EXAMPLE) as f:
            lines = f.read().splitlines()
        write_lines(lines)
    with open(SECRETS) as f:
        return f.read().splitlines()


def write_lines(lines):
    fd = os.open(SECRETS, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as f:
        f.write("\n".join(lines) + "\n")
    os.chmod(SECRETS, 0o600)


def value_of(lines, name):
    for line in lines:
        if line.startswith(name + "="):
            return line[len(name) + 1:]
    return None


def set_value(lines, name, value):
    for i, line in enumerate(lines):
        if line.startswith(name + "="):
            lines[i] = f"{name}={value}"
            return
    lines.append(f"{name}={value}")


def apksigner():
    sdk = os.environ.get("ANDROID_HOME") or os.path.expanduser("~/Android/Sdk")
    tools = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "apksigner")))
    if not tools:
        sys.exit("apksigner not found under the Android SDK's build-tools")
    return tools[-1]


def certificate_digests(apks):
    digests = []
    for apk in apks:
        out = subprocess.run([apksigner(), "verify", "--print-certs", apk], capture_output=True, text=True, check=True).stdout
        for digest in re.findall(r"certificate SHA-256 digest: ([0-9a-f]{64})", out):
            if digest not in digests:
                digests.append(digest)
    return digests


def push(filled):
    """`supabase secrets set` with only the filled-in lines; its output names no values."""
    if not filled:
        sys.exit("Nothing to push")
    fd, path = tempfile.mkstemp(prefix="precut-secrets-", suffix=".env")
    try:
        with os.fdopen(fd, "w") as f:
            f.write("\n".join(filled) + "\n")
        result = subprocess.run(
            ["npx", "--yes", "supabase@2.120.0", "secrets", "set", "--env-file", path],
            cwd=ROOT, stdin=subprocess.DEVNULL, capture_output=True, text=True,
        )
    finally:
        os.remove(path)
    if result.returncode != 0:
        sys.exit("supabase secrets set failed (exit %d)" % result.returncode)
    print("pushed:", ", ".join(line.split("=", 1)[0] for line in filled))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--service-account")
    parser.add_argument("--elevenlabs-key-file")
    parser.add_argument("--apk", action="append", default=[])
    parser.add_argument("--cert", action="append", default=[])
    parser.add_argument("--push", action="store_true")
    args = parser.parse_args()
    lines = read_lines()

    if args.service_account:
        try:
            with open(args.service_account) as f:
                key = json.load(f)
        except (OSError, ValueError):
            sys.exit(f"{args.service_account} is missing, empty or not JSON: creating the key failed, so run the gcloud keys create line again")
        if "client_email" not in key or "private_key" not in key:
            sys.exit("That file isn't a service-account key")
        line = json.dumps(key, separators=(",", ":"))
        if "'" in line:
            sys.exit("The key contains a single quote; it can't go in the secrets file as-is")
        set_value(lines, "GOOGLE_SERVICE_ACCOUNT_JSON", f"'{line}'")

    if args.elevenlabs_key_file:
        with open(args.elevenlabs_key_file) as f:
            key = f.read().strip()
        if not re.fullmatch(r"sk_[0-9a-f]{32,}", key):
            sys.exit("That doesn't look like an ElevenLabs API key (sk_ followed by hex)")
        set_value(lines, "ELEVENLABS_API_KEY", key)

    if not value_of(lines, "INSTALL_TOKEN_SECRET"):
        set_value(lines, "INSTALL_TOKEN_SECRET", secrets.token_urlsafe(48))

    if args.apk or args.cert:
        digests = certificate_digests(args.apk) + [c.strip().replace(":", "").lower() for c in args.cert]
        if not digests:
            sys.exit("No signing certificates found in those APKs")
        known = [d for d in (value_of(lines, "PLAY_CERT_SHA256") or "").split(",") if d.strip()]
        set_value(lines, "PLAY_CERT_SHA256", ",".join(dict.fromkeys(known + digests)))

    write_lines(lines)
    if args.push:
        push([line for line in lines if re.match(r"^[A-Z0-9_]+=.+", line)])
    for name in ["ELEVENLABS_API_KEY", "ELEVENLABS_ZERO_RETENTION", "GOOGLE_SERVICE_ACCOUNT_JSON", "INSTALL_TOKEN_SECRET", "PLAY_CERT_SHA256"]:
        print(f"{name}: {'set' if value_of(lines, name) else 'EMPTY'}")


if __name__ == "__main__":
    main()
