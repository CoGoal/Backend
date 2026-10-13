"""Helpers for the local Compose Kafka; only isolated test profiles should be published."""
from pathlib import Path
import datetime
import json
import os
import subprocess
import uuid
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]

def compose(*arguments, input=None, timeout=45, check=True):
    return subprocess.run(["docker", "compose", *arguments], cwd=ROOT, input=input,
                          text=True, encoding="utf-8", capture_output=True, timeout=timeout, check=check)

def settings():
    path=ROOT / ".env"
    return dict(line.split("=",1) for line in path.read_text().splitlines()
                if "=" in line and not line.startswith("#")) if path.exists() else {}

def topic():
    return os.environ.get("KAFKA_PROFILE_TOPIC", settings().get("KAFKA_PROFILE_TOPIC", "cogoal.user-profiles.v1"))

def publish_raw(key, payload):
    compose("exec", "-T", "kafka", "/opt/kafka/bin/kafka-console-producer.sh",
            "--bootstrap-server", "kafka:29092", "--topic", topic(),
            "--property", "parse.key=true", "--property", "key.separator=\t",
            "--producer-property", "acks=all", input=str(key)+"\t"+payload+"\n")

def publish(profile, event_id=None):
    envelope={"eventId": event_id or str(uuid.uuid4()), "schemaVersion": 1,
              "eventType": "UserRegistered" if profile["version"]==1 else "UserProfileUpdated",
              "occurredAt": datetime.datetime.now(datetime.timezone.utc).isoformat(), "user": profile}
    publish_raw(profile["id"], json.dumps(envelope))

def database_query(service, sql):
    config=settings()
    prefix="AUTH" if service=="auth" else "MAIN"
    url=config.get(prefix+"_DB_URL", "")
    if not url:
        username=config.get("POSTGRES_USER", "postgres")
        database="cg-auth" if service=="auth" else "CoGoal"
        return compose("exec", "-T", "postgres", "psql", "-U", username, "-d", database, "-t", "-A", "-c", sql).stdout.strip()
    uri=urlsplit(url.removeprefix("jdbc:"))
    env=os.environ.copy()
    env["PGPASSWORD"]=config[prefix+"_DB_PASSWORD"]
    result=subprocess.run(["docker", "run", "--rm", "-e", "PGPASSWORD", "-e", "PGSSLMODE=require",
                           "-e", "PGCHANNELBINDING=require", "-e", "PGCONNECT_TIMEOUT=20", "postgres:18", "psql",
                           "-h", uri.hostname, "-p", str(uri.port or 5432), "-U", config[prefix+"_DB_USERNAME"],
                           "-d", uri.path.lstrip("/"), "-t", "-A", "-v", "ON_ERROR_STOP=1", "-c", sql],
                          env=env, text=True, encoding="utf-8", capture_output=True, check=True, timeout=45)
    return result.stdout.strip()

def pending(user_id):
    return int(database_query("auth", "SELECT count(*) FROM main_profile_outbox WHERE user_id="+str(int(user_id))+" AND status='PENDING'"))

def account_version(user_id):
    return int(database_query("main", "SELECT profile_version FROM user_profiles WHERE id="+str(int(user_id))) or "0")
