# Executed only by booking-load-test.py inside its disposable MySQL/Redis fixture.
import queue
active_streams=[]
def portal_login(phone,portal):
 jar=http.cookiejar.CookieJar();op=urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPCookieProcessor(jar));h={'X-Studio-Portal':portal,'X-Studio-Session-Scope':str(uuid.uuid4())}
 code,c=call('/auth/csrf',headers=h,op=op);assert code==200
 h[c['data']['headerName']]=c['data']['token'];code,b=call('/auth/login',{'phone':phone,'password':fixture_password},h,op);assert code==200,(code,b)
 code,c=call('/auth/csrf',headers=h,op=op);h[c['data']['headerName']]=c['data']['token'];h['Cookie']='; '.join(c.name+'='+c.value for c in jar);return h
class Stream:
 def __init__(self,h,catalog=False,target=None):
  req=urllib.request.Request((target or bases[-1])+('/sync/catalog-events' if catalog else '/sync/events'),headers=h)
  self.response=urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req,timeout=20);assert 'text/event-stream' in self.response.headers['Content-Type'];self.events=queue.Queue();self.ended=threading.Event();active_streams.append(self)
  def read():
   data=[]
   try:
    while True:
     line=self.response.readline()
     if not line:break
     line=line.decode().strip()
     if line.startswith('data:'):data.append(line[5:].strip())
     if not line and data:self.events.put(json.loads('\n'.join(data)));data=[]
   except (OSError,ValueError,AttributeError):pass
   finally:self.ended.set()
  threading.Thread(target=read,daemon=True).start()
 def next(self):return self.events.get(timeout=4)
 def quiet(self):
  try:raise AssertionError(('unexpected notification',self.events.get(timeout=.3)))
  except queue.Empty:pass
 def close(self):
  try:self.response.fp.raw._sock.shutdown(socket.SHUT_RDWR)
  except (OSError,AttributeError):pass
  self.response.close();self.ended.wait(2)
def publish(*scopes):
 args=[]
 for scope in scopes:args.extend(['shiguang:sync:v1:'+scope,str(uuid.uuid4())])
 redis('MSET',*args);redis('PUBLISH','shiguang:sync:events:v1',','.join(scopes))
try:
 for uid,phone,role in [(8001,'18200000001','ADMIN'),(8002,'18200000002','RECEPTIONIST')]:
  sql(f"INSERT INTO sys_user(id,phone,password_hash,display_name) VALUES({uid},'{phone}','{passwordHash}','隔离SSE账号');INSERT INTO sys_user_role SELECT {uid},id FROM sys_role WHERE code='{role}';")
 adminHeaders=portal_login('18200000001','admin');deskHeaders=portal_login('18200000002','staff');photoHeaders=portal_login('18100000100','staff')
 customer=Stream(sessions[99]);other=Stream(sessions[98]);adminStream=Stream(adminHeaders);desk=Stream(deskHeaders);photo=Stream(photoHeaders);public=Stream({},True)
 streams=[customer,other,adminStream,desk,photo,public]
 for stream in streams:assert stream.next()['available']
 # An actual business write on instance A must notify listeners connected to instance B.
 day=(datetime.date.today()+datetime.timedelta(days=12)).isoformat()
 status,body=call('/appointments',{'packageId':100,'photographerId':100,'startTime':day+'T09:00:00','requestId':str(uuid.uuid4()),'notes':''},sessions[99],target=bases[0]);assert status==200,(status,body)
 for stream in [customer,adminStream,desk,photo]:assert stream.next()['available']
 other.quiet();assert public.next()["available"]
 print('SSE real booking: customer/reception/admin/photographer notified across two instances; unrelated customer unchanged; public availability version refreshed.',flush=True)
 # Individual notification remains strictly scoped to its customer.
 for stream in streams:
  while not stream.events.empty():stream.events.get_nowait()
 publish('user:1099');assert other.next()['available'];customer.quiet();adminStream.quiet();desk.quiet();photo.quiet();public.quiet()
 old=call('/sync/version',headers=sessions[99])[1]['data']['version'];customer.close();publish('user:1100');customer2=Stream(sessions[99]);assert customer2.next()['version']!=old
 print('SSE disconnected client receives current version on reconnect.',flush=True)
 req=urllib.request.Request(base+'/auth/logout',data=b'{}',headers={'Content-Type':'application/json',**sessions[99]})
 with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req,timeout=5) as response:assert response.status==204
 publish('user:1100');assert customer2.ended.wait(4),'Logged-out stream was not closed'
 try:Stream(sessions[99]);raise AssertionError('Logged-out account can reconnect')
 except urllib.error.HTTPError as e:assert e.code==401
 try:Stream({**photoHeaders,'X-Studio-Portal':'admin'});raise AssertionError('Cross-portal access accepted')
 except urllib.error.HTTPError as e:assert e.code in (401,403)
 publish('configuration');assert public.next()['available']
 print('SSE logout closes old stream, reconnect requires login, cross-portal access rejected, public stream only catalog changes.',flush=True)
finally:
 for stream in active_streams:
  try:stream.close()
  except Exception:pass
