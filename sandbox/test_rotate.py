import ssl, urllib.request, json, uuid, time
from datetime import datetime, timezone

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

node_id = "node_84d9aa157eb6df5a"
msg_id = f"msg_{int(time.time())}_{uuid.uuid4().hex[:8]}"
timestamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"

# 发送 hello 请求，请求 rotate_secret
envelope = {
    "protocol": "gep-a2a",
    "protocol_version": "1.0.0",
    "message_type": "hello",
    "message_id": msg_id,
    "sender_id": node_id,
    "timestamp": timestamp,
    "payload": {
        "node_id": node_id,
        "rotate_secret": True
    }
}

print("Sending hello with rotate_secret...")
data = json.dumps(envelope).encode("utf-8")
req = urllib.request.Request(
    "https://evomap.ai/a2a/hello",
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
