import {test} from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import {readFile} from 'node:fs/promises'
import {webcrypto} from 'node:crypto'
const source=path=>readFile(new URL('../src/'+path,import.meta.url),'utf8')
async function moduleFrom(path,dependencies={},globals={}) {
 const context=vm.createContext(globals), module=new vm.SourceTextModule(await source(path),{context})
 await module.link(async name=>{
  const values=dependencies[name];assert.ok(values, name)
  return new vm.SyntheticModule(Object.keys(values),function(){for(const [key,value] of Object.entries(values))this.setExport(key,value)},{context})
 })
 await module.evaluate();return module.namespace
}
const storage=()=>{const values=new Map();return {getItem:key=>values.get(key),setItem:(key,value)=>values.set(key,value),removeItem:key=>values.delete(key)}}
test('all portals and independent tabs have distinct stable scopes; reload retains them',async()=>{
 const first=storage(),second=storage()
 const a=await moduleFrom('utils/portalSession.js',{}, {window:{sessionStorage:first},crypto:webcrypto})
 const b=await moduleFrom('utils/portalSession.js',{}, {window:{sessionStorage:second},crypto:webcrypto})
 const scopes=['customer','staff','admin'].map(p=>a.getSessionScope(p))
 assert.equal(new Set(scopes).size,3)
 for(const p of ['customer','staff','admin'])assert.notEqual(a.getSessionScope(p),b.getSessionScope(p))
 const reload=await moduleFrom('utils/portalSession.js',{}, {window:{sessionStorage:first},crypto:webcrypto})
 assert.equal(reload.getSessionScope('customer'),a.getSessionScope('customer'))
 const old=a.getSessionScope('customer');a.renewSessionScope('customer');assert.notEqual(a.getSessionScope('customer'),old)
 assert.equal(a.getSessionScope('staff'),scopes[1])
})
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject}}
async function authHarness(){
 const reads=[],login=deferred(),logout=deferred();let portal='customer'
 const auth=await moduleFrom('composables/useAuth.js',{
  vue:{reactive:x=>x},
  '../utils/portal':{getPortal:()=>portal,setPortal:p=>portal=p},
  '../api/authApi':{getCurrentUser:p=>{const d=deferred();reads.push({...d,portal:p});return d.promise},loginAccount:()=>login.promise,logoutAccount:()=>logout.promise}
 })
 return {auth,reads,login,logout}
}
test('late customer identity never overwrites active admin identity',async()=>{
 const {auth,reads}=await authHarness();const old=auth.loadUser();auth.selectPortal('admin');const current=auth.loadUser()
 reads[1].resolve({id:1});await current;reads[0].resolve({id:2});await old
 assert.equal(auth.auth.user.id,1);auth.selectPortal('customer');assert.equal(auth.auth.user.id,2)
})
test('late identity cannot restore account after logout',async()=>{
 const {auth,reads,logout}=await authHarness();const old=auth.loadUser();const signout=auth.logout()
 logout.resolve();await signout;reads[0].resolve({id:2});await old;assert.equal(auth.auth.user,null)
})
test('newer identity response wins and login cannot complete in a different portal',async()=>{
 const {auth,reads,login}=await authHarness();const old=auth.loadUser(),newer=auth.loadUser()
 reads[1].resolve({id:3});await newer;reads[0].resolve({id:2});await old;assert.equal(auth.auth.user.id,3)
 const signing=auth.login({});auth.selectPortal('admin');login.resolve({id:4});await assert.rejects(signing);assert.equal(auth.auth.user,null)
})

async function httpHarness(){
 const interceptors={},scopes={customer:'customer-a',staff:'staff-a',admin:'admin-a'},csrfRequests=[]
 let active='customer'
 const client={
  interceptors:{request:{use:fn=>interceptors.request=fn},response:{use:(ok,bad)=>{interceptors.ok=ok;interceptors.bad=bad}}},
  get:async(path,config)=>{csrfRequests.push(config.headers);return {data:{data:{headerName:'X-CSRF-TOKEN',token:config.headers['X-Studio-Session-Scope']}}}}
 }
 class CanceledError extends Error {}
 const http=await moduleFrom('utils/http.js',{
  axios:{default:{create:()=>client,CanceledError}},
  './portal':{getPortal:()=>active},
  './portalSession':{getSessionScope:p=>scopes[p],renewSessionScope:p=>scopes[p]+='-new'}
 })
 return {http,interceptors,scopes,csrfRequests,setPortal:p=>active=p}
}
test('explicit portal and CSRF stay bound to original request during portal switch',async()=>{
 const h=await httpHarness();h.setPortal('admin')
 const config=await h.interceptors.request({method:'post',headers:{'X-Studio-Portal':'customer','X-Studio-Session-Scope':'customer-a'}})
 assert.equal(config.headers['X-Studio-Portal'],'customer');assert.equal(config.headers['X-CSRF-TOKEN'],'customer-a')
 const admin=await h.interceptors.request({method:'post',headers:{}});assert.equal(admin.headers['X-CSRF-TOKEN'],'admin-a')
 assert.equal(h.csrfRequests.length,2)
})
test('responses from replaced account are discarded and new login obtains new CSRF',async()=>{
 const h=await httpHarness();const old=await h.interceptors.request({method:'post',headers:{}})
 h.http.beginLogin('customer')
 assert.throws(()=>h.interceptors.ok({config:old}),/登录状态已切换/)
 const fresh=await h.interceptors.request({method:'post',headers:{}})
 assert.equal(fresh.headers['X-CSRF-TOKEN'],'customer-a-new')
 assert.equal(h.interceptors.ok({config:fresh}).config,fresh)
})

test('reopening restores last successful login per portal without switching existing staff tabs',async()=>{
 const local=storage()
 const tab=()=>moduleFrom('utils/portalSession.js',{}, {window:{sessionStorage:storage(),localStorage:local},crypto:webcrypto})
 const a=await tab();const reception=a.getSessionScope('staff');a.rememberSession('staff',reception)
 const customer=a.getSessionScope('customer');a.rememberSession('customer',customer)
 const b=await tab();assert.equal(b.getSessionScope('staff'),reception);assert.equal(b.getSessionScope('customer'),customer)
 const photo=b.renewSessionScope('staff');b.rememberSession('staff',photo)
 assert.equal(a.getSessionScope('staff'),reception)
 const reopened=await tab();assert.equal(reopened.getSessionScope('staff'),photo)
 a.forgetSession('staff',reception);assert.equal((await tab()).getSessionScope('staff'),photo)
 b.forgetSession('staff',photo);assert.notEqual((await tab()).getSessionScope('staff'),photo)
 assert.equal((await tab()).getSessionScope('customer'),customer)
})
test('failed or superseded login does not replace remembered account',async()=>{
 const local=storage();const a=await moduleFrom('utils/portalSession.js',{}, {window:{sessionStorage:storage(),localStorage:local},crypto:webcrypto})
 const previous=a.getSessionScope('staff');a.rememberSession('staff',previous)
 const abandoned=a.renewSessionScope('staff');a.renewSessionScope('staff');a.rememberSession('staff',abandoned)
 const reopened=await moduleFrom('utils/portalSession.js',{}, {window:{sessionStorage:storage(),localStorage:local},crypto:webcrypto})
 assert.equal(reopened.getSessionScope('staff'),previous)
})


test('logout clears identity but retains successful login form in this tab and portal',async()=>{
 const {auth,login,logout}=await authHarness()
 const payload={phone:'13800000001',password:'example-password'}
 const signing=auth.login(payload)
 login.resolve({id:2});await signing
 payload.password='changed-after-submit'
 assert.equal(auth.rememberedLogin('customer').password,'example-password')
 const signout=auth.logout();logout.resolve();await signout
 assert.equal(auth.auth.user,null)
 assert.equal(auth.rememberedLogin('customer').phone,'13800000001')
 assert.equal(auth.rememberedLogin('customer').password,'example-password')
 for(const portal of ['staff','admin']){
  assert.equal(auth.rememberedLogin(portal).phone,'')
  assert.equal(auth.rememberedLogin(portal).password,'')
 }
 const copy=auth.rememberedLogin('customer');copy.password='edited'
 assert.equal(auth.rememberedLogin('customer').password,'example-password')
 const otherTab=await authHarness()
 assert.equal(otherTab.auth.rememberedLogin('customer').password,'')
})
test('failed and superseded logins do not retain submitted credentials',async()=>{
 const failed=await authHarness()
 const attempt=failed.auth.login({phone:'13800000001',password:'wrong'})
 failed.login.reject(new Error('invalid login'));await assert.rejects(attempt)
 assert.equal(failed.auth.rememberedLogin('customer').phone,'')
 const switched=await authHarness()
 const pending=switched.auth.login({phone:'13800000001',password:'example'})
 switched.auth.selectPortal('admin');switched.login.resolve({id:2})
 await assert.rejects(pending)
 assert.equal(switched.auth.rememberedLogin('customer').password,'')
 assert.equal(switched.auth.rememberedLogin('admin').password,'')
})

test('shared-login logout invalidates only matching scope, rotates it and removes listener', async()=>{
 const listeners=new Map(),tabStore=storage(),local=storage(),invalidated=[]
 const window={sessionStorage:tabStore,localStorage:local,addEventListener:(name,fn)=>listeners.set(name,fn),removeEventListener:(name,fn)=>{if(listeners.get(name)===fn)listeners.delete(name)}}
 const session=await moduleFrom('utils/portalSession.js',{}, {window,crypto:webcrypto})
 const staff=session.getSessionScope('staff'),customer=session.getSessionScope('customer')
 const stop=session.watchSessionLogout(portal=>invalidated.push(portal))
 listeners.get('storage')({key:'shiguang.staff.logout',newValue:JSON.stringify({scope:customer})})
 listeners.get('storage')({key:'shiguang.staff.logout',newValue:'malformed'})
 assert.equal(invalidated.length,0)
 listeners.get('storage')({key:'shiguang.staff.logout',newValue:JSON.stringify({scope:staff})})
 assert.deepEqual(invalidated,['staff'])
 assert.notEqual(session.getSessionScope('staff'),staff)
 assert.equal(session.getSessionScope('customer'),customer)
 // A delayed logout event for the former login cannot revoke the new login.
 const fresh=session.renewSessionScope('staff')
 listeners.get('storage')({key:'shiguang.staff.logout',newValue:JSON.stringify({scope:staff})})
 assert.equal(session.getSessionScope('staff'),fresh)
 assert.deepEqual(invalidated,['staff'])
 stop();assert.equal(listeners.size,0)
})
test('remote logout cannot be undone by a late identity response',async()=>{
 const {auth,reads}=await authHarness();auth.selectPortal('staff')
 const loading=auth.loadUser();auth.invalidateSession('staff')
 reads[0].resolve({id:8});await loading
 assert.equal(auth.auth.user,null);assert.equal(auth.auth.loaded,true)
})
