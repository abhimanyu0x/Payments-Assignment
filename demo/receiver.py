"""Local-only receiver. Verifies signatures and deduplicates events in SQLite."""
import base64, hashlib, hmac, json, os, sqlite3, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
SECRET = base64.b64decode(os.environ.get("WEBHOOK_SECRET", "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="))
DB = os.environ.get("RECEIVER_DB", "/data/events.sqlite")
os.makedirs(os.path.dirname(DB) or ".", exist_ok=True)
with sqlite3.connect(DB) as conn:
    conn.execute("CREATE TABLE IF NOT EXISTS events(id TEXT PRIMARY KEY, payload TEXT NOT NULL, received_at INTEGER NOT NULL)")
class Receiver(BaseHTTPRequestHandler):
    def reply(self, status, body):
        data = json.dumps(body).encode()
        self.send_response(status); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data)
    def do_GET(self):
        if self.path == "/health": return self.reply(200, {"status": "up"})
        if self.path != "/events": return self.reply(404, {})
        with sqlite3.connect(DB) as conn: rows = conn.execute("SELECT payload FROM events ORDER BY received_at DESC LIMIT 100").fetchall()
        self.reply(200, {"data": [json.loads(row[0]) for row in rows]})
    def do_POST(self):
        if self.path != "/webhooks": return self.reply(404, {})
        length = int(self.headers.get("Content-Length", "0"))
        if length < 1 or length > 65536: return self.reply(413, {})
        raw = self.rfile.read(length)
        try:
            timestamp = self.headers["X-Webhook-Timestamp"]
            if abs(time.time() - int(timestamp)) > 300: raise ValueError("stale")
            expected = "v1=" + hmac.new(SECRET, timestamp.encode() + b"." + raw, hashlib.sha256).hexdigest()
            if not hmac.compare_digest(expected, self.headers.get("X-Webhook-Signature", "")): raise ValueError("signature")
            event = json.loads(raw)
            if event["id"] != self.headers.get("X-Webhook-Id"): raise ValueError("id")
        except (ValueError, KeyError, TypeError): return self.reply(400, {"error": "invalid_webhook"})
        with sqlite3.connect(DB) as conn:
            inserted = conn.execute("INSERT OR IGNORE INTO events VALUES (?,?,?)", (event["id"], raw.decode(), int(time.time()))).rowcount
        print(json.dumps({"event_id": event["id"], "type": event["type"], "duplicate": not inserted}), flush=True)
        self.reply(200, {"received": True, "duplicate": not inserted})
if __name__ == "__main__": ThreadingHTTPServer(("0.0.0.0", 8090), Receiver).serve_forever()
