"""Exercise Kafka delivery, outages, duplicates and DLT in the running local Compose.
Temporarily stops main and Kafka; always restarts them. Creates isolated test accounts.
Run after tests/smoke_auth.py: python tests/smoke_kafka.py.
"""
import json
import time
import uuid
from smoke_auth import call, expect
from kafka_helpers import compose, publish, publish_raw, pending, topic, account_version

def wait_for(fn, label, seconds=90):
    until=time.monotonic()+seconds
    while time.monotonic()<until:
        value=fn()
        if value: return value
        time.sleep(1)
    raise AssertionError("Timed out: "+label)

def register():
    username="kafka_"+uuid.uuid4().hex[:16]
    response=expect("POST", "/api/v1/auth/register", 201,
                    {"email":username+"@example.com", "username":username, "name":"Kafka тест", "password":"kafka-test-password"})
    return response

def profile(response, name=None):
    status, value=call("GET", "/api/v1/users/me", token=response["accessToken"])
    return value if status==200 and (name is None or value["name"]==name) else None

def main():
    # Auth may publish while main is down; the broker keeps the event for the returning consumer.
    try:
        compose("stop", "cogoal-main")
        registered=register()
        wait_for(lambda: pending(registered["user"]["id"])==0, "broker acknowledgement while main is stopped")
    finally:
        compose("start", "cogoal-main")
    restored=wait_for(lambda: profile(registered), "profile after consumer restart")
    assert restored["coins"]==100
    print("PASS: registration and publication succeed while main is down; profile arrives after restart")

    # A broker outage must leave the event in the transactional outbox.
    try:
        compose("stop", "kafka")
        queued=register()
        assert pending(queued["user"]["id"])>0
    finally:
        compose("start", "kafka")
    recovered=wait_for(lambda: profile(queued), "outbox replay after broker restart", seconds=150)
    assert recovered["coins"]==100
    print("PASS: broker outage preserves registration and the pending profile event")

    user=queued["user"]
    expect("PATCH", "/api/v1/users/me", 200, {"name":"Имя main", "avatarUrl":"/files/main-avatar.png"}, queued["accessToken"])
    snapshot={"id":user["id"], "email":user["email"], "username":user["username"],
              "name":"Kafka latest", "avatarUrl":None, "role":"USER", "version":3, "legacyId":None}
    event_id=str(uuid.uuid4())
    publish(snapshot,event_id);publish(snapshot,event_id)
    older=dict(snapshot,name="Kafka older",version=2)
    publish(older)
    wait_for(lambda: account_version(user["id"])==3, "account update and older event handling")
    assert profile(queued,"Имя main")["avatarUrl"]=="/files/main-avatar.png"

    # A malformed record goes to DLT; a subsequent record with the same key must still be processed.
    marker="invalid-kafka-test-"+uuid.uuid4().hex
    publish_raw(user["id"],marker)
    publish(dict(snapshot,name="Kafka recovery",version=4))
    wait_for(lambda: account_version(user["id"])==4, "consumer progress after poison record")
    final=profile(queued,"Имя main")
    assert final["avatarUrl"]=="/files/main-avatar.png"
    assert final["coins"]==100 and final["createdAt"]==recovered["createdAt"]
    dead=compose("exec","-T","kafka","/opt/kafka/bin/kafka-console-consumer.sh",
                 "--bootstrap-server","kafka:29092","--topic",topic()+".DLT","--from-beginning",
                 "--timeout-ms","5000",timeout=25,check=False)
    assert marker in dead.stdout, "Invalid event was not retained in DLT"
    print("PASS: duplicates, older versions, durable DLT and continued consumption preserve the balance")

if __name__=="__main__":
    main()
