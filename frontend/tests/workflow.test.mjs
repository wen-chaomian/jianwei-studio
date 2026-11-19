import {refundablePayments,notificationTitle,readableContent} from '../src/utils/workflow.js'
import {test} from 'node:test'
import assert from 'node:assert/strict'
import vm from 'node:vm'
import {readFile} from 'node:fs/promises'
const deferred=()=>{let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b});return {promise,resolve,reject}}
async function harness(){
 const route={params:{id:'1'},query:{}},reads=[],writes=[],watchers=[]
 const src=await readFile(new URL('../src/composables/useAppointmentDetail.js',import.meta.url),'utf8')
 const deps={'./useChangeSync':{useChangeSync:()=>{}},'./RequestPanel.vue':{default:{}},'../utils/workflow':{refundablePayments,readableContent},vue:{ref:value=>({value}),computed:fn=>({get value(){return fn()}}),onMounted:()=>{},watch:(...v)=>watchers.push(v)},'vue-router':{useRoute:()=>route},'../api/operationsApi':{get:path=>{const d=deferred();reads.push({...d,path});return d.promise},post:(path,payload)=>{const d=deferred();writes.push({...d,path,payload});return d.promise},patch:()=>Promise.resolve(),command:()=>Math.random().toString()},'./useAuth':{auth:{user:{roles:['ADMIN']}}},'../utils/portal':{getPortal:()=> 'admin'},'../utils/bookingFormat':{dateText:x=>x,money:x=>x,statusText:{},today:()=> '2026-10-26'}}
 const context=vm.createContext({}),m=new vm.SourceTextModule(src+'\nconst {load,open,submit,item,activity,mode,error,notice}=useAppointmentDetail(); export {load,open,submit,item,activity,mode,error,notice};',{context})
 await m.link(name=>new vm.SyntheticModule(Object.keys(deps[name]),function(){for(const [k,v] of Object.entries(deps[name]))this.setExport(k,v)},{context}));await m.evaluate()
 return {page:m.namespace,route,reads,writes}
}
test('late appointment response cannot overwrite a newly opened booking',async()=>{
 const {page,route,reads}=await harness();const old=page.load();route.params.id='2';const current=page.load();reads[2].resolve({id:2});reads[3].resolve({version:0});await current;reads[0].resolve({id:1});reads[1].resolve({version:0});await old;assert.equal(page.item.value.id,2)
})
test('failed detail load clears old actionable data',async()=>{
 const {page,route,reads}=await harness();const old=page.load();reads[0].resolve({id:1});reads[1].resolve({version:0});await old;route.params.id='2';const current=page.load();assert.equal(page.item.value,null);reads[2].reject({userMessage:'无权访问'});reads[3].resolve({});await current;assert.equal(page.activity.value,null);assert.equal(page.error.value,'无权访问')
})
test('double submit sends one command and cannot change operation during flight',async()=>{
 const {page,reads,writes,route}=await harness();const load=page.load();reads[0].resolve({id:1});reads[1].resolve({version:0});await load;page.open('cancel');const first=page.submit();page.open('payment');await page.submit();assert.equal(writes.length,1);assert.equal(page.mode.value,'cancel');assert.equal(writes[0].path,'/appointments/1/cancel');route.params.id='2';writes[0].resolve({});await first;assert.equal(page.notice.value,'');assert.equal(reads.length,2)
})

test('refund choices subtract each original payment and omit exhausted entries',()=>{
 const choices=refundablePayments([{id:1,type:'PAYMENT',amount:'0.30'},{id:2,type:'PAYMENT',amount:'100.00'},{type:'REFUND',originalId:1,amount:'0.10'},{type:'REFUND',originalId:1,amount:'0.20'},{type:'REFUND',originalId:2,amount:'15.01'}]);assert.equal(choices.length,1);assert.equal(choices[0].remainingCents,8499)
})
test('legacy messages use Chinese titles and readable dates',()=>{
 assert.equal(notificationTitle('reschedule'),'预约已改期');assert.equal(readableContent('2026-10-28T10:00:00 → 2026-10-29T13:30:00'),'2026-10-28 10:00 → 2026-10-29 13:30')
})
test('check-in submission needs no typed explanation',async()=>{
 const {page,reads,writes,route}=await harness();const loaded=page.load();reads[0].resolve({id:1});reads[1].resolve({version:0});await loaded;page.open('check-in');const submitted=page.submit();assert.equal(writes[0].payload.reason,'');route.params.id='2';writes[0].resolve({});await submitted
})
