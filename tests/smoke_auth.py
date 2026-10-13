"""Exercise the running local Compose stack; creates a uniquely named test account.
Run: python tests/smoke_auth.py. Secrets and tokens are never printed.
"""
from pathlib import Path
import json
import os
import re
import subprocess
import time
import urllib.request
import urllib.error
import uuid
from kafka_helpers import publish

ROOT = Path(__file__).resolve().parents[1]
BASE = os.environ.get("API_BASE_URL", "http://localhost:8080")
MAIL = os.environ.get("MAILPIT_URL", "http://localhost:8025")

def call(method, path, body=None, token=None, base=BASE):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(base + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        response = urllib.request.urlopen(req, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    raw = response.read()
    return response.status, json.loads(raw) if raw else None

def expect(method, path, status, body=None, token=None):
    actual, result = call(method, path, body, token)
    assert actual == status, (method, path, actual, result.get("code") if isinstance(result, dict) else "")
    return result

def wait_for(fn, label):
    for _ in range(45):
        result = fn()
        if result:
            return result
        time.sleep(1)
    raise AssertionError("Timed out: " + label)

def mail_token(email):
    status, messages = call("GET", "/api/v1/messages", base=MAIL)
    assert status == 200
    for message in messages.get("messages", []):
        if any(address.get("Address") == email for address in message.get("To", [])):
            _, detail = call("GET", "/api/v1/message/" + message["ID"], base=MAIL)
            found = re.search(r"[?]token=([A-Za-z0-9_-]+)", detail.get("Text", ""))
            if found:
                return found.group(1)
    return None

def replay(snapshot):
    publish(snapshot)

def main():
    username = "smoke_" + uuid.uuid4().hex[:16]
    email = username + "@example.com"
    password = "test-password-1"
    expect("GET", "/api/v1/users/me", 401)
    registered = expect("POST", "/api/v1/auth/register", 201, {"email": email, "username": username, "name": "Тест", "password": password})
    user_id = registered["user"]["id"]
    assert "name" not in registered["user"] and "avatarUrl" not in registered["user"]
    assert isinstance(user_id, int) and user_id > 0
    access = registered["accessToken"]
    for identifier in [email.upper(), "  " + username.upper() + "  "]:
        logged = expect("POST", "/api/v1/auth/login", 200, {"login": identifier, "password": password})
        assert logged["user"]["id"] == user_id
    expect("POST", "/api/v1/auth/login", 400, {"email": email, "password": password})
    duplicate = expect("POST", "/api/v1/auth/register", 409, {"email": email, "username": username, "name": "Тест", "password": password})
    assert duplicate["code"] == "EMAIL_TAKEN"
    profile = wait_for(lambda: (lambda result: result[1] if result[0] == 200 else None)(call("GET", "/api/v1/users/me", token=access)), "profile creation")
    assert profile["name"] == "Тест" and profile["coins"] == 100
    snapshot = {"id": user_id, "email": email, "username": username, "name": "Тест", "avatarUrl": None, "role": "USER", "version": 1}
    replay(snapshot)
    renamed = username + "x"
    expect("PATCH", "/api/v1/users/me", 400, {"username": renamed}, access)
    expect("PATCH", "/api/v1/users/me", 200, {"name": "Новое имя"}, access)
    expect("PATCH", "/api/v1/auth/me/username", 200, {"username": renamed}, access)
    expect("POST", "/api/v1/auth/login", 200, {"login": renamed, "password": password})
    expect("POST", "/api/v1/auth/login", 401, {"login": username, "password": password})
    wait_for(lambda: (lambda p: p if p.get("username") == renamed else None)(expect("GET", "/api/v1/users/me", 200, token=access)), "profile update")
    replay(snapshot)
    updated = expect("GET", "/api/v1/users/me", 200, token=access)
    assert updated["username"] == renamed and updated["coins"] == 100 and updated["name"] == "Новое имя"
    avatar = expect("PATCH", "/api/v1/users/me", 200, {"avatarUrl": "/files/avatar.png"}, access)
    assert avatar["avatarUrl"] == "/files/avatar.png"
    preserved = expect("PATCH", "/api/v1/users/me", 200, {"name": "Имя без смены аватара"}, access)
    assert preserved["avatarUrl"] == "/files/avatar.png"
    cleared = expect("PATCH", "/api/v1/users/me", 200, {"avatarUrl": None}, access)
    assert cleared["avatarUrl"] is None
    expect("POST", "/api/v1/auth/password/forgot", 202, {"email": email})
    reset = wait_for(lambda: mail_token(email), "reset email")
    expect("POST", "/api/v1/auth/password/reset", 204, {"token": reset, "newPassword": "test-password-2"})
    expect("POST", "/api/v1/auth/password/reset", 410, {"token": reset, "newPassword": "test-password-3"})
    expect("POST", "/api/v1/auth/refresh", 401, {"refreshToken": registered["refreshToken"]})
    logged = expect("POST", "/api/v1/auth/login", 200, {"login": renamed, "password": "test-password-2"})
    access = logged["accessToken"]
    new_email = username + "@new.example.com"
    expect("POST", "/api/v1/users/me/email-change", 202, {"newEmail": new_email, "password": "test-password-2"}, access)
    confirmation = wait_for(lambda: mail_token(new_email), "email confirmation")
    expect("POST", "/api/v1/auth/email/confirm", 204, {"token": confirmation})
    wait_for(lambda: (lambda p: p if p.get("email") == new_email else None)(expect("GET", "/api/v1/users/me", 200, token=access)), "email synchronization")
    assert expect("GET", "/api/v1/users/me", 200, token=access)["name"] == "Имя без смены аватара"
    logged = expect("POST", "/api/v1/auth/login", 200, {"login": new_email, "password": "test-password-2"})
    pair = expect("POST", "/api/v1/auth/refresh", 200, {"refreshToken": logged["refreshToken"]})
    expect("POST", "/api/v1/auth/logout", 401, {"refreshToken": pair["refreshToken"]})
    expect("POST", "/api/v1/auth/logout", 204, {"refreshToken": pair["refreshToken"]}, pair["accessToken"])
    expect("POST", "/api/v1/auth/refresh", 401, {"refreshToken": pair["refreshToken"]})
    expect("POST", "/internal/users", 404, snapshot)
    # Alternating identifiers must share the five-attempt limit.
    for i in range(5):
        expect("POST", "/api/v1/auth/login", 401, {"login": new_email if i % 2 else renamed, "password": "wrong-password"})
    locked = expect("POST", "/api/v1/auth/login", 423, {"login": renamed, "password": "test-password-2"})
    assert locked["code"] == "ACCOUNT_LOCKED"
    print("PASS: gateway, registration, email/username login, profile sync, idempotency, SMTP, reset, email change, refresh/logout, shared lockout, internal isolation")

if __name__ == "__main__":
    main()
