import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createChatDrafts } from '../src/utils/chatDrafts.js'
import { confirmationRows } from '../src/utils/confirmationRows.js'
function storage() { const m = new Map(); return { getItem:k=>m.get(k),setItem:(k,v)=>m.set(k,v),removeItem:k=>m.delete(k) } }
test('drafts survive remounts and remain isolated by account, portal and conversation',()=>{
 const s=storage(), a=createChatDrafts(s)
 a.save('customer:1','a','未发送内容'); a.select('customer:1','a'); a.save('customer:1','b','另一对话')
 const b=createChatDrafts(s)
 assert.equal(b.read('customer:1','a'),'未发送内容'); assert.equal(b.last('customer:1'),'a')
 assert.equal(b.read('customer:2','a'),''); assert.equal(b.read('staff:1','a'),'')
 b.save('customer:1','a',''); assert.equal(b.read('customer:1','a'),''); assert.equal(b.read('customer:1','b'),'另一对话')
 b.remove('customer:1','b'); assert.equal(b.read('customer:1','b'),'')
})
test('unavailable browser storage does not break input',()=>{
 const s=createChatDrafts({getItem(){throw Error()},setItem(){throw Error()},removeItem(){throw Error()}})
 s.save('a','b','x'); s.remove('a','b'); assert.equal(s.read('a','b'),'')
})
test('confirmation fields preserve money, dates, multiline text and legacy information',()=>{
 const r=confirmationRows('拍摄套餐：清新证件照\n拍摄时间：2026-11-09T15:00:00\n预约金额：¥129.00\n备注：第一行\n第二行')
 assert.deepEqual(r[1],{label:'拍摄时间',value:'2026-11-09 15:00'}); assert.equal(r[2].value,'¥129.00'); assert.equal(r.at(-1).value,'第二行')
 assert.equal(confirmationRows('摄影师 · 2026-11-09T15:00 · ¥129.00').length,3)
})

import vm from 'node:vm'
import { readFile } from 'node:fs/promises'
import { ref, computed, watch } from 'vue'
async function chatHarness() {
 const s=storage(), hooks=[]; let sendResolve, sendReject
 const source=await readFile(new URL('../src/composables/useAgentChat.js',import.meta.url),'utf8')
 const api={conversation:async()=>({messages:[],has_more:false}),sendMessage:()=>new Promise((r,j)=>{sendResolve=r;sendReject=j}),conversations:async()=>({items:[]}),agentStatus:async()=>({model_ready:true})}
 const deps={vue:{ref,computed,watch,onMounted:()=>{},onBeforeUnmount:f=>hooks.push(f)},'vue-router':{useRoute:()=>({meta:{area:'customer'},query:{}}),useRouter:()=>({replace:()=>{}})},'./useAuth':{auth:{user:{id:1}}},'./useChangeSync':{useChangeSync:()=>{}},'../utils/portalSession':{sessionHeaders:()=>({}),getSessionScope:()=> 'scope'},'../utils/agentStream':{watchAgentRun:()=>({stop(){}})},'../api/agentApi':api,'../utils/chatDrafts':{createChatDrafts}}
 const context=vm.createContext({window:{sessionStorage:s,removeEventListener(){}},crypto:{randomUUID:()=> 'uuid'}})
 const mod=new vm.SourceTextModule(source+'\nexport const chat = useAgentChat();',{context})
 await mod.link(n=>new vm.SyntheticModule(Object.keys(deps[n]),function(){for(const [k,v] of Object.entries(deps[n]))this.setExport(k,v)},{context}));await mod.evaluate()
 return {chat:mod.namespace.chat,resolve:v=>sendResolve(v),reject:v=>sendReject(v),unmount:()=>hooks.forEach(f=>f()),storage:s}
}
test('switching conversations restores each draft and unmount does not erase it',async()=>{
 const h=await chatHarness(),c=h.chat;await c.select('a');c.draft.value='first';await c.select('b');c.draft.value='second';await c.select('a');assert.equal(c.draft.value,'first');h.unmount();assert.equal(createChatDrafts(h.storage).read('customer:1','a'),'first')
})
test('send acknowledgement cannot erase text typed while the request is pending',async()=>{
 const h=await chatHarness(),c=h.chat;await c.select('a');c.draft.value='send';const task=c.send();c.draft.value='next message';h.resolve({id:'run'});await task;assert.equal(c.draft.value,'next message')
})
test('failed sends retain the original draft',async()=>{
 const h=await chatHarness(),c=h.chat;await c.select('a');c.draft.value='retry me';const task=c.send();h.reject({userMessage:'连接中断'});await task;assert.equal(c.draft.value,'retry me');assert.match(c.error.value,/连接中断/)
})
test('a response from the previous conversation cannot erase the current draft',async()=>{
 const h=await chatHarness(),c=h.chat;await c.select('a');c.draft.value='send';const task=c.send();await c.select('b');c.draft.value='keep b';h.resolve({id:'run'});await task;assert.equal(c.draft.value,'keep b');assert.equal(c.selected.value,'b')
})
test('legacy booking confirmation is displayed as labeled fields',()=>{
 const r=confirmationRows('清新证件照 · 陈老师 · 2026-11-09T15:00 · 30分钟 · ¥129.00  拍摄证件照')
 assert.equal(r[0].label,'拍摄套餐');assert.equal(r[4].value,'¥129.00');assert.equal(r[5].value,'拍摄证件照')
})
