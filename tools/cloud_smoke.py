#!/usr/bin/env python3
"""Smoke test for the deployed (or local) `cloud` Edge Function (docs/CLOUD_API.md).

Deployed: checks routing and refusals, that Supabase accepts a 10 MB upload body (an
unregistered upload must come back as the function's own JSON refusal, not a platform error),
and that Google can be asked about Play Integrity tokens (it refuses a made-up one).
With --local (a stack running REQUIRE_INTEGRITY=false and TRANSCRIBER=fake): the whole flow:
register, status, a 10 MB upload, the minutes charged, delete.

Usage: python3 -I tools/cloud_smoke.py https://<ref>.supabase.co/functions/v1/cloud [--local]
"""
import json
import sys
import urllib.error
import urllib.request
import uuid

results = []


def check(name, ok, detail):
    results.append(ok)
    print(f"{'PASS' if ok else 'FAIL'}  {name}: {detail}")


def call(base, method, path, headers=None, body=None, api="1"):
    all_headers = {"x-region": "eu-west-1", **(headers or {})}
    if api:
        all_headers["x-precut-api"] = api
    request = urllib.request.Request(base + path, data=body, method=method, headers=all_headers)
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return response.status, json.loads(response.read() or b"null")
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, raw[:200].decode(errors="replace")


def code(body):
    return body.get("error", {}).get("code") if isinstance(body, dict) else None


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    base = sys.argv[1].rstrip("/")
    local = "--local" in sys.argv
    ten_mb = bytes(10 * 1024 * 1024 - 1024)

    status, body = call(base, "GET", "/status", api=None)
    check("old clients are told to update", status == 426 and code(body) == "upgrade_required", f"{status} {body}")
    status, body = call(base, "GET", "/status")
    check("no install token: not registered", status == 401 and code(body) == "not_registered", f"{status} {body}")
    status, body = call(base, "GET", "/nothing")
    check("unknown route", status == 404, f"{status}")
    status, body = call(base, "POST", "/transcribe", {"x-install-token": "bogus", "content-type": "audio/mp4", "x-audio-duration-ms": "1000"}, ten_mb)
    check("a 10 MB upload reaches the function", status == 401 and code(body) == "not_registered", f"{status} {body}")

    if not local:
        # Google itself must refuse a made-up token (403 app_not_verified). A 503
        # integrity_unavailable means the service account or the Play Integrity API isn't set up.
        status, body = call(base, "POST", "/register", {"content-type": "application/json", "x-integrity-token": "made-up-token"},
                            json.dumps({"installId": str(uuid.uuid4())}).encode())
        check("Google refuses a made-up Play Integrity token", status == 403 and code(body) == "app_not_verified", f"{status} {body}")

    if local:
        install = str(uuid.uuid4())
        status, body = call(base, "POST", "/register", {"content-type": "application/json", "x-integrity-token": "local"}, json.dumps({"installId": install}).encode())
        token = body.get("installToken") if isinstance(body, dict) else None
        check("register", status == 200 and token and body["status"]["secondsLeft"] == 1800, f"{status} {body if status != 200 else body['status']}")
        headers = {"x-install-token": token or "", "x-integrity-token": "local", "content-type": "audio/mp4", "x-audio-duration-ms": "3000"}
        status, body = call(base, "POST", "/transcribe?lang=auto", headers, ten_mb)
        words = body.get("transcript", {}).get("words") if isinstance(body, dict) else None
        check("a 10 MB upload is transcribed and 3 s charged", status == 200 and words and body["secondsLeft"] == 1797, f"{status} {body}")
        status, body = call(base, "GET", "/status", {"x-install-token": token or ""})
        check("status after the upload", status == 200 and body["status"]["secondsLeft"] == 1797, f"{status} {body}")
        status, body = call(base, "DELETE", "/me", {"x-install-token": token or ""})
        check("delete my cloud data", status == 200 and body == {"deleted": True}, f"{status} {body}")
        status, body = call(base, "GET", "/status", {"x-install-token": token or ""})
        check("deleted install is not registered", status == 401 and code(body) == "not_registered", f"{status} {body}")

    print(f"\n{sum(bool(r) for r in results)}/{len(results)} passed")
    sys.exit(0 if all(results) else 1)


if __name__ == "__main__":
    main()
