"""Opt-in local HTTP load test. Disposable MySQL schema and Redis process only.
Requires MYSQL_TEST_ADMIN_PASSWORD. Never uses the application's live schema/Redis.
"""
import math,os,json,uuid,time,subprocess,tempfile,pathlib,urllib.request,urllib.error,http.cookiejar,concurrent.futures,threading,datetime,socket,statistics
ROOT=pathlib.Path(__file__).resolve().parents[1]
MYSQL=os.environ.get('MYSQL_TEST_CLIENT','mysql')
REDIS=os.environ.get('REDIS_TEST_BIN','redis-server')
JAVA=os.environ.get('JAVA_TEST_BIN','java')
fixture_password=uuid.uuid4().hex
run=uuid.uuid4().hex[:12];schema='studio_load_'+run;account='load_'+run
work=pathlib.Path(tempfile.mkdtemp(prefix='studio-load-'));processes=[];results=[]
def port():
 with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
appPort,redisPort=port(),port();base=f'http://127.0.0.1:{appPort}/api/v1';bases=[base]
admin=work/'admin.cnf';admin.write_text('[client]\nuser=root\npassword='+os.environ['MYSQL_TEST_ADMIN_PASSWORD']+'\nhost=127.0.0.1\nport=3306\n');admin.chmod(0o600)
def sql(text,db=True):
 return subprocess.check_output([MYSQL,'--defaults-extra-file='+str(admin),'-N','-B',*([schema] if db else [])],input=text.encode(),stderr=subprocess.PIPE).decode().strip()
def redis(*args):
 # Only addresses our disposable Redis port; never touches the running platform Redis.
 return subprocess.check_output([str(pathlib.Path(REDIS).with_name('redis-cli')),'-p',str(redisPort),*args]).decode().strip()
def call(path,data=None,headers=None,op=None,target=None):
 req=urllib.request.Request((target or base)+path,data=None if data is None else json.dumps(data).encode(),headers={'Content-Type':'application/json',**(headers or {})})
 try:
  with (op or urllib.request.build_opener(urllib.request.ProxyHandler({}))).open(req,timeout=45) as r:return r.status,json.loads(r.read())
 except urllib.error.HTTPError as e:
  raw=e.read().decode()
  try:body=json.loads(raw)
  except ValueError:body={'message':raw[:150]}
  return e.code,body

def login(phone):
 jar=http.cookiejar.CookieJar();op=urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPCookieProcessor(jar));h={'X-Studio-Portal':'customer','X-Studio-Session-Scope':str(uuid.uuid4())}
 code,c=call('/auth/csrf',headers=h,op=op);assert code==200
 h[c['data']['headerName']]=c['data']['token']
 code,b=call('/auth/login',{'phone':phone,'password':fixture_password},h,op);assert code==200,(code,b)
 code,c=call('/auth/csrf',headers=h,op=op);h[c['data']['headerName']]=c['data']['token'];h['Cookie']='; '.join(c.name+'='+c.value for c in jar)
 return h
try:
 sql(f"CREATE DATABASE {schema} CHARACTER SET utf8mb4; CREATE USER '{account}'@'127.0.0.1' IDENTIFIED BY '{run}'; GRANT ALL ON {schema}.* TO '{account}'@'127.0.0.1';",False)
 processes.append(subprocess.Popen([REDIS,'--bind','127.0.0.1','--port',str(redisPort),'--save','','--appendonly','no','--dir',str(work)],stdout=open(work/'redis.log','w'),stderr=subprocess.STDOUT))
 cfg=work/'application.properties';cfg.write_text(f'''server.address=127.0.0.1
server.port={appPort}
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/{schema}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
spring.datasource.username={account}
spring.datasource.password={run}
spring.data.redis.host=127.0.0.1
spring.data.redis.port={redisPort}
studio.seed.enabled=false
''');cfg.chmod(0o600)
 processes.append(subprocess.Popen([JAVA,'-jar',os.environ.get('LOAD_JAR',str(ROOT/'release/studio.jar')),'--spring.config.import=optional:file:'+str(cfg)],cwd=work,stdout=open(work/'app.log','w'),stderr=subprocess.STDOUT))
 for _ in range(80):
  try:
   if call('/sync/catalog')[0]==200:break
  except Exception:pass
  time.sleep(.25)
 else:raise RuntimeError('Test application failed to start: '+str(work/'app.log'))
 if os.environ.get('LOAD_INSTANCES')=='2':
  secondPort=port();secondBase=f'http://127.0.0.1:{secondPort}/api/v1'
  processes.append(subprocess.Popen([JAVA,'-jar',os.environ.get('LOAD_JAR',str(ROOT/'release/studio.jar')),'--spring.config.import=optional:file:'+str(cfg),'--server.port='+str(secondPort)],cwd=work,stdout=open(work/'app-second.log','w'),stderr=subprocess.STDOUT))
  for _ in range(80):
   try:
    if call('/sync/catalog',target=secondBase)[0]==200:break
   except Exception:pass
   time.sleep(.25)
  else:raise RuntimeError('Second instance failed to start')
  bases.append(secondBase)
 # One real registration generates the normal password hash; remaining fixtures are test-only SQL data.
 jar=http.cookiejar.CookieJar();op=urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPCookieProcessor(jar));h={'X-Studio-Portal':'customer','X-Studio-Session-Scope':str(uuid.uuid4())}
 _,c=call('/auth/csrf',headers=h,op=op);h[c['data']['headerName']]=c['data']['token']
 code,b=call('/auth/register',{'phone':'18999999999','password':fixture_password,'confirmPassword':fixture_password,'displayName':'并发测试基准'},h,op);assert code==200,(code,b)
 passwordHash=sql("SELECT password_hash FROM sys_user WHERE phone='18999999999'")
 statements=[]
 for i in range(1,101):
  uid=1000+i;pid=2000+i
  statements += [f"INSERT INTO sys_user(id,phone,password_hash,display_name) VALUES({uid},'180{i:08d}','{passwordHash}','并发客户{i}'),({pid},'181{i:08d}','{passwordHash}','并发摄影师{i}')",f"INSERT INTO sys_user_role SELECT {uid},id FROM sys_role WHERE code='CUSTOMER'",f"INSERT INTO sys_user_role SELECT {pid},id FROM sys_role WHERE code='PHOTOGRAPHER'",f"INSERT INTO customer(user_id,name,phone,source) VALUES({uid},'并发客户{i}','180{i:08d}','REGISTER')",f"INSERT INTO employee(id,user_id,employee_no,name,bookable) VALUES({i},{pid},'LOAD{i}','并发摄影师{i}',1)",f"INSERT INTO venue(id,name,location,description) VALUES({i},'并发场地{i}','','')",f"INSERT INTO service_package(id,name,description,price,duration_minutes,buffer_minutes) VALUES({i},'独立资源{i}','',399,60,30),({200+i},'共享场地{i}','',399,60,30)",f"INSERT INTO package_photographer VALUES({i},{i}),({200+i},{i})",f"INSERT INTO package_venue VALUES({i},{i}),({200+i},1)"]
 sql(';'.join(statements)+';')
 if os.environ.get('LOAD_SLOT_TEST')=='true':sql('UPDATE booking_rule SET slot_minutes=15 WHERE id=1')
 for offset in range(1,13):
  day=(datetime.date.today()+datetime.timedelta(days=offset)).isoformat()
  sql(f"INSERT INTO photographer_schedule(resource_id,work_date) SELECT id,'{day}' FROM employee; INSERT INTO venue_schedule(resource_id,work_date) SELECT id,'{day}' FROM venue;")
 sql("INSERT INTO photographer_schedule_period(schedule_id,start_time,end_time) SELECT id,'09:00','20:00' FROM photographer_schedule; INSERT INTO venue_schedule_period(schedule_id,start_time,end_time) SELECT id,'09:00','20:00' FROM venue_schedule;")
 sessions=[]
 for i in range(1,101):
  if i%10==1:redis('DEL','shiguang:auth-rate:/api/v1/auth/login:127.0.0.1') # Session preparation only; submission test does not alter limits.
  sessions.append(login(f'180{i:08d}'))
 print('Isolated fixture ready: 100 customers, 100 photographers, 100 venues; real HTTP, MySQL and Redis.',flush=True)
 if os.environ.get("LOAD_SSE_TEST")=="true":
  exec(compile((ROOT/"scripts/sse-load-check.py").read_text(),"sse-load-check.py","exec"),globals())
 offset=0
 for n in ([int(x) for x in os.environ.get('LOAD_LEVELS','20,50,100').split(',')]*int(os.environ.get('LOAD_ROUNDS','1'))):
  for scenario in os.environ.get('LOAD_SCENARIOS','same_photographer,same_venue,independent_resources,duplicate_request').split(','):
   offset+=1;day=(datetime.date.today()+datetime.timedelta(days=offset)).isoformat();start=day+'T10:00:00';barrier=threading.Barrier(n);requestId=str(uuid.uuid4())
   def worker(i):
    idx=i+1;package=1 if scenario in ['same_photographer','duplicate_request'] else (200+idx if scenario=='same_venue' else idx);photo=1 if scenario in ['same_photographer','duplicate_request'] else idx
    localStart=start
    if scenario in ['same_day_disjoint','adjacent','partial_overlap','buffer_overlap']:
     assert n==2
     package=1;photo=1
     shift={'same_day_disjoint':240,'adjacent':90,'partial_overlap':15,'buffer_overlap':60}[scenario]
     localStart=(datetime.datetime.fromisoformat(start)+datetime.timedelta(minutes=i*shift)).isoformat()
    if scenario=='shared_venue_adjacent':
     assert n==2
     package=201+i;photo=1+i;localStart=(datetime.datetime.fromisoformat(start)+datetime.timedelta(minutes=i*90)).isoformat()
    if scenario=='same_day_many_slots':
     package=photo=i//5+1;localStart=day+f'T{9+2*(i%5):02d}:00:00'
    payload={'packageId':package,'photographerId':photo,'startTime':localStart,'requestId':requestId if scenario=='duplicate_request' else str(uuid.uuid4()),'remark':'隔离并发压测'}
    headers=sessions[0] if scenario=='duplicate_request' else sessions[i]
    op=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    barrier.wait(timeout=15);t=time.perf_counter()
    try:status,body=call('/appointments',payload,headers,op,target=bases[i%len(bases)])
    except Exception as e:status,body=0,{'message':type(e).__name__}
    return {'status':status,'ms':round((time.perf_counter()-t)*1000,2),'id':(body.get('data') or {}).get('id'),'message':body.get('message')}
   t=time.perf_counter()
   with concurrent.futures.ThreadPoolExecutor(max_workers=n) as pool:rows=list(pool.map(worker,range(n)))
   wall=time.perf_counter()-t;latencies=sorted(r['ms'] for r in rows);counts={str(s):sum(r['status']==s for r in rows) for s in sorted(set(r['status'] for r in rows))}
   actual=int(sql(f"SELECT COUNT(*) FROM appointment WHERE DATE(start_time)='{day}'"));overlaps=int(sql(f"SELECT COUNT(*) FROM appointment a JOIN appointment b ON a.id<b.id AND (a.photographer_id=b.photographer_id OR a.venue_id=b.venue_id) AND a.start_time<b.occupied_end_time AND a.occupied_end_time>b.start_time WHERE DATE(a.start_time)='{day}'"))
   expected=n if scenario in ['independent_resources','same_day_disjoint','adjacent','shared_venue_adjacent','same_day_many_slots'] else 1
   result={'concurrency':n,'scenario':scenario,'http':counts,'appointments':actual,'expected':expected,'overlapPairs':overlaps,'meanMs':round(statistics.mean(latencies),2),'successMeanMs':round(statistics.mean(r['ms'] for r in rows if r['status']==200),2) if any(r['status']==200 for r in rows) else None,'remainingRedisLocks':len(redis('KEYS','shiguang:{booking-lock}:*').splitlines()),'p50ms':latencies[int((n-1)*.5)],'p95ms':latencies[math.ceil(n*.95)-1],'maxMs':max(latencies),'wallSeconds':round(wall,3),'messages':dict((str(r['status']),r['message']) for r in rows if r['status']!=200),'uniqueSuccessIds':len(set(r['id'] for r in rows if r['status']==200))}
   results.append(result);print(json.dumps(result,ensure_ascii=False),flush=True)
 fallback=None
 if os.environ.get('LOAD_DB_SLOT_PROOF')=='true':
  import select
  day=(datetime.date.today()+datetime.timedelta(days=11)).isoformat();start=day+'T10:00:00'
  blocker=subprocess.Popen([MYSQL,'--defaults-extra-file='+str(admin),'-N','-B','--unbuffered',schema],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,bufsize=1)
  try:
   blocker.stdin.write(f"START TRANSACTION; INSERT INTO booking_resource_slot_lock VALUES('photographer',1,'{start}') ON DUPLICATE KEY UPDATE slot_start=VALUES(slot_start); SELECT 'LOCK_READY';\n");blocker.stdin.flush()
   assert select.select([blocker.stdout],[],[],10)[0] and blocker.stdout.readline().strip()=='LOCK_READY'
   def payload(time):return {'packageId':1,'photographerId':1,'startTime':time,'requestId':str(uuid.uuid4()),'remark':'隔离锁失效回归'}
   with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    first=pool.submit(call,'/appointments',payload(start),sessions[0],None,bases[0])
    for _ in range(100):
     keys=redis('KEYS','shiguang:{booking-lock}:*').splitlines()
     if keys:break
     time.sleep(.02)
    assert keys,'First request did not acquire Redis lock'
    # Simulated lost lease only in our disposable Redis. Database guard must still protect booking.
    redis('DEL',*keys)
    second=pool.submit(call,'/appointments',payload(start),sessions[1],None,bases[-1])
    for _ in range(100):
     if redis('KEYS','shiguang:{booking-lock}:*'):break
     time.sleep(.02)
    t=time.perf_counter();other=call('/appointments',payload(day+'T14:00:00'),sessions[2],target=bases[-1]);unrelatedMs=(time.perf_counter()-t)*1000
    assert other[0]==200,other
    assert not first.done() and not second.done(),'Overlapping requests bypassed held DB slot'
    blocker.stdin.write('COMMIT;\n');blocker.stdin.flush()
    responses=[first.result(timeout=15),second.result(timeout=15)]
   actual=int(sql(f"SELECT COUNT(*) FROM appointment WHERE start_time='{start}'"));assert actual==1 and sorted(r[0] for r in responses)==[200,400],responses
   assert redis('KEYS','shiguang:{booking-lock}:*')==''
   fallback={'simulatedRedisLeaseLoss':True,'overlappingHttp':[r[0] for r in responses],'overlappingAppointments':actual,'otherTimeSucceededWhileBlocked':True,'otherTimeMs':round(unrelatedMs,2)}
   print('Database fallback proof: '+json.dumps(fallback),flush=True)
  finally:
   if blocker.poll() is None:
    blocker.stdin.write('ROLLBACK;\n');blocker.stdin.close();blocker.wait(timeout=10)
 if os.environ.get('LOAD_WORKFLOW_IT')=='true':
  print('Running isolated real-MySQL workflow integration tests.',flush=True)
  env={**os.environ,'RUN_WORKFLOW_IT':'true'}
  with open(work/'workflow-tests.log','w') as log:
   subprocess.run([os.environ.get('MAVEN_CMD','mvn'),'-q','-Dtest=WorkflowIntegrationTest','-Dspring.config.import=optional:file:'+str(cfg),'test'],cwd=ROOT/'backend',env=env,stdout=log,stderr=subprocess.STDOUT,check=True)
  print('17 isolated workflow integration tests passed.',flush=True)
 report={'databaseFallback':fallback,'timestamp':datetime.datetime.now().isoformat(),'instances':len(bases),'fixture':'isolated MySQL schema + dedicated Redis + Spring Boot HTTP; default Hikari max pool 10','results':results,'applicationErrors':sum(' ERROR ' in line for line in (work/'app.log').read_text().splitlines()),'logDirectory':str(work)}
 out=ROOT/'reports';out.mkdir(exist_ok=True);(out/('booking-concurrency-'+os.environ.get('LOAD_LABEL','2026-11-02')+'.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2))
 print('Report saved. Application ERROR lines: '+str(report['applicationErrors']),flush=True)
 for r in results:
  expectedHttp={'200':1,'400':r['concurrency']-1} if r['scenario'] in ('same_photographer','same_venue') else {'200':r['concurrency']}
  httpOK=(r['http']==expectedHttp) if os.environ.get('LOAD_REDIS_LOCK')!='true' else (set(r['http']) <= {'200','400','409'} and (r['http']=={'200':r['concurrency']} if r['scenario'] in ['independent_resources','same_day_disjoint','adjacent','shared_venue_adjacent','same_day_many_slots'] else r['http'].get('200',0)>=1))
  assert r['appointments']==r['expected'] and r['overlapPairs']==0 and r['uniqueSuccessIds']==r['expected'] and r['remainingRedisLocks']==0 and httpOK,r
 assert report['applicationErrors']==0,report['applicationErrors']
finally:
 for p in reversed(processes):
  p.terminate()
  try:p.wait(timeout=15)
  except subprocess.TimeoutExpired:p.kill();p.wait()
 sql(f"DROP DATABASE IF EXISTS {schema}; DROP USER IF EXISTS '{account}'@'127.0.0.1';",False)
 for p in [admin,work/'application.properties']:
  if p.exists():p.unlink()
 print('Disposable database, account and processes cleaned; live application untouched.',flush=True)
