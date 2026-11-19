import test from 'node:test'
import assert from 'node:assert/strict'
import {watchAgentRun} from '../src/utils/agentStream.js'
const tick=()=>new Promise(r=>setImmediate(r))
test('agent snapshots replace output, only matching run is delivered, terminal event closes',async()=>{
 const updates=[],states=[];let request
 const body='event: snapshot\ndata: {"id":"wrong","status":"running","output":"secret"}\n\nevent: snapshot\ndata: {"id":"one","status":"running","output":"你"}\n\nevent: snapshot\ndata: {"id":"one","status":"completed","output":"你好"}\n\n'
 const stream=watchAgentRun({runId:'one',headers:{'X-Studio-Portal':'staff','X-Studio-Session-Scope':'scope'},onSnapshot:x=>updates.push(x),onState:s=>states.push(s),fetcher:async(url,options)=>{request=options;return new Response(body,{headers:{'Content-Type':'text/event-stream'}})}})
 await tick();assert.deepEqual(updates.map(x=>x.output),['你','你好']);assert.equal(request.headers['X-Studio-Session-Scope'],'scope');stream.stop()
})
test('account switch stops stream and drops late chunks',async()=>{
 let pipe,request;const updates=[]
 const stream=watchAgentRun({runId:'one',headers:{},onSnapshot:x=>updates.push(x),onState:()=>{},fetcher:async(url,opts)=>{request=opts;return new Response(new ReadableStream({start(c){pipe=c}}),{headers:{'Content-Type':'text/event-stream'}})}})
 await tick();stream.stop();assert.ok(request.signal.aborted)
 pipe.enqueue(new TextEncoder().encode('event: snapshot\ndata: {"id":"one","status":"completed","output":"old account"}\n\n'));await tick();assert.equal(updates.length,0)
})
test('unauthorized stream is not retried',async()=>{
 let count=0;const states=[]
 const stream=watchAgentRun({runId:'one',headers:{},onSnapshot:()=>assert.fail(),onState:s=>states.push(s),fetcher:async()=>{count++;return new Response('',{status:403})}})
 await tick();assert.equal(count,1);assert.equal(states.at(-1),'unauthorized');stream.stop()
})
