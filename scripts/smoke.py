"""Run against the actual Compose app + PSP. Uses only Python's standard library."""
import concurrent.futures, json, os, time, uuid, subprocess
from urllib.request import Request, urlopen
from urllib.error import HTTPError
BASE=os.environ.get('API_URL','http://localhost:8080/api/v1')
KEY=os.environ.get('API_KEY','demo.local-demo-secret-change-for-real-use')
def call(path, body=None, key=None):
 headers={'Authorization':'Bearer '+KEY,'Content-Type':'application/json'}
 if key: headers['Idempotency-Key']=key
 req=Request(BASE+path,data=None if body is None else json.dumps(body).encode(),headers=headers)
 try:
  with urlopen(req,timeout=10) as r: return r.status,json.load(r)
 except HTTPError as e: return e.code,json.load(e)
def assert_one_processor_operation(attempt_id):
 # Operation UUID is validated before inclusion in a local operator query.
 operation = str(uuid.UUID(attempt_id))
 sql = "SELECT count(*),max(post_count) FROM mock_psp.operations WHERE operation_id='" + operation + "'"
 result = subprocess.check_output(['docker','compose','exec','-T','db','psql','-U','postgres','-d','dodo','-Atc',sql], text=True).strip()
 assert result == '1|1', result

def invoice():
 status,c=call('/customers',{'name':'Smoke Test','email':'smoke@example.com'}); assert status==201,c
 status,i=call('/invoices',{'customer_id':c['id'],'due_date':'2026-10-20','items':[{'description':'Work','quantity':2,'unit_amount_cents':2500}]});assert status==201,i
 return i['id']
def wait_for(path, expected, timeout=90):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  status,r=call(path); assert status==200,r
  if r.get('status')==expected:return r
  time.sleep(.5)
 raise AssertionError(r)
def run():
 for _ in range(90):
  try:
   if call('/customers')[0]==200:break
  except OSError: pass
  time.sleep(1)
 else:raise RuntimeError('API not ready')
 invoice_id=invoice()
 with concurrent.futures.ThreadPoolExecutor(max_workers=20) as pool:
  results=list(pool.map(lambda _:call('/invoices/'+invoice_id+'/pay',{'card_token':'tok_success'},str(uuid.uuid4())),range(20)))
 accepted=[r for s,r in results if s==202];assert len(accepted)==1,results;assert all(s in [202,409] for s,_ in results),results
 result=wait_for('/payment-attempts/'+accepted[0]['payment_attempt_id'],'succeeded');assert_one_processor_operation(accepted[0]['payment_attempt_id']);assert call('/invoices/'+invoice_id)[1]['state']=='paid'
 for token,outcome in [('tok_card_declined','failed'),('tok_insufficient_funds','failed'),('tok_timeout','succeeded'),('tok_network_error','failed')]:
  invoice_id=invoice();key=str(uuid.uuid4());started=time.monotonic();status,first=call('/invoices/'+invoice_id+'/pay',{'card_token':token},key);assert status==202,first;assert time.monotonic()-started<5
  result=wait_for('/payment-attempts/'+first['payment_attempt_id'],outcome)
  status,replay=call('/invoices/'+invoice_id+'/pay',{'card_token':token},key);assert status==202 and replay==first;assert_one_processor_operation(first['payment_attempt_id'])
  expected='paid' if outcome=='succeeded' else 'open';assert call('/invoices/'+invoice_id)[1]['state']==expected
  print(token, outcome, 'PASS')
 status,events=call('/events');assert status==200 and events['data']
 end=time.monotonic()+30
 while time.monotonic()<end:
  status,deliveries=call('/webhook-deliveries?limit=100')
  if status==200 and deliveries['data'] and all(d['status']=='delivered' for d in deliveries['data']):break
  time.sleep(1)
 else:raise AssertionError('Not all recent deliveries confirmed; run with demo profile and fresh test data.')
 print('Concurrent acceptance, all mock outcomes, replay and final states: PASS')
if __name__=='__main__': run()
