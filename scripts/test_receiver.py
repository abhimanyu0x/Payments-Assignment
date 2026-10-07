import base64, hashlib, hmac, json, os, socket, subprocess, sys, tempfile, time, unittest
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError
ROOT = Path(__file__).resolve().parents[1]
SECRET = bytes([1]) * 32
class ReceiverTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.tmp = tempfile.TemporaryDirectory()
  env = dict(os.environ, RECEIVER_DB=cls.tmp.name + '/events.sqlite', WEBHOOK_SECRET=base64.b64encode(SECRET).decode())
  cls.process = subprocess.Popen([sys.executable, str(ROOT/'demo/receiver.py')], env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
  for _ in range(50):
   try:
    with urlopen('http://localhost:8090/health', timeout=.2): return
   except OSError: time.sleep(.1)
  raise RuntimeError('Receiver did not start')
 @classmethod
 def tearDownClass(cls):
  cls.process.terminate(); cls.process.wait(timeout=5); cls.tmp.cleanup()
 def send(self, body, timestamp=None, signature=None):
  timestamp = str(int(time.time()) if timestamp is None else timestamp)
  signature = signature or 'v1=' + hmac.new(SECRET, timestamp.encode()+b'.'+body, hashlib.sha256).hexdigest()
  request = Request('http://localhost:8090/webhooks', data=body, headers={'Content-Type':'application/json','X-Webhook-Timestamp':timestamp,'X-Webhook-Signature':signature,'X-Webhook-Id':'event-test'})
  try:
   with urlopen(request, timeout=2) as response: return response.status, json.load(response)
  except HTTPError as e: return e.code, json.load(e)
 def test_signature_and_deduplication(self):
  body=b'{"id":"event-test","type":"invoice.paid","data":{}}'
  status, value=self.send(body); self.assertEqual(200,status); self.assertFalse(value['duplicate'])
  status, value=self.send(body); self.assertEqual(200,status); self.assertTrue(value['duplicate'])
 def test_bad_signature(self): self.assertEqual(400,self.send(b'{"id":"event-test"}', signature='v1=bad')[0])
 def test_stale_and_future_timestamps(self):
  for offset in [-600,600]: self.assertEqual(400,self.send(b'{"id":"event-test"}', int(time.time())+offset)[0])
if __name__=='__main__': unittest.main(verbosity=2)
