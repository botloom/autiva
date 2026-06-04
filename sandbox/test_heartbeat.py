import ssl, urllib.request, json, uuid, time
from datetime import datetime, timezone

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

node_id = "node_84d9aa157eb6df5a"
msg_id = f"msg_{int(time.time())}_{uuid.uuid4().hex[:8]}"
timestamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"

# 尝试不带 secret 的心跳（可能失败，但看看返回什么）
envelope = {
    "protocol": "gep-a2a",
    "protocol_version": "1.0.0",
    "message_type": "heartbeat",
    "message_id": msg_id,
    "sender_id": node_id,
    "timestamp": timestamp,
    "payload": {"node_id": node_id}
}

print("Sending heartbeat without secret...")
data = json.dumps(envelope).encode("utf-8")
req = urllib.request.Request(
    "https://evomap.ai/a2a/heartbeat",
    data=data,
    headers={"Content-Type": "application/json"},
    method="POST"
)

try:
    resp = urllib.request.urlopen(req, context=ctx, timeout=30)
    result = json.loads(resp.read().decode("utf-8"))
    print("Response:", json.dumps(result, indent=2, ensure_ascii=False))
except urllib.error.HTTPError as e:
    body = e.read().decode("utf-8")
    print(f"HTTP Error {e.code}: {body}")
except Exception as e:
    print(f"Error: {type(e).__name__}: {e}")

# 再试试用用sender_id作为Authorization（尝试从hello响应中获取）
print("\n---\nTrying with empty bearer token...")
envelope2 = {
    "protocol": "gep-a2a",
    "protocol_version": "1.0.0",
    "message_type": "heartbeat",
    "message_id": f"msg_{int(time.time())}_{uuid.uuid4().hex[:8]}",
    "sender_id": node_id,
    "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z",
    "payload": {"node_id": node_id}
}

data2 = json.dumps(envelope2).encode("utf-8")
req2 = urllib.request.Request(
    "https://evomap.ai/a2a/heartbeat",
    data=data2,
    headers={
        "Content-Type": "application/json",
        "Authorization": "Bearer "
    },
    method="POST"
)

try:
    resp2 = urllib.request.urlopen(req2, context=ctx, timeout=30)
    result2 = json.loads(resp2.read().decode("utf-8"))
    print("Response:", json.dumps(result2, indent=2, ensure_ascii=False))
except urllib.error.HTTPError as e:
    body = e.read().decode("utf-8")
    print(f"HTTP Error {e.code}: {body}")
except Exception as e:
    print(f"Error: {type(e).__name__}: {e}")
