const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const assert=require('node:assert/strict')
// Operates on seeded, disposable data only. Requires a locally installed Playwright and Chromium.
const base=process.env.AGENT_E2E_URL || 'http://127.0.0.1:18080'
if (process.env.AGENT_E2E_DISPOSABLE !== 'yes' || !['localhost','127.0.0.1'].includes(new URL(base).hostname)) throw Error('需要明确指定隔离的本机验收环境')
const day=n=>new Date(Date.now()+n*86400000).toLocaleDateString('en-CA',{timeZone:'Asia/Shanghai'})
const seedPassword = process.env.STUDIO_SEED_PASSWORD
if (!seedPassword) throw Error('请提供隔离环境初始化时使用的 STUDIO_SEED_PASSWORD')
let browser
async function login(portal,phone,pw){
 const p=await browser.newPage()
 await p.goto(base+'/'+portal+'/login')
 await p.getByPlaceholder('请输入11位手机号').fill(phone)
 await p.getByPlaceholder('请输入密码').fill(pw)
 await p.getByPlaceholder('请输入密码').press('Enter')
 await p.waitForURL(base+'/'+portal)
 await p.goto(base+'/'+portal+'/assistant')
 return p
}
async function chat(p,prompt){
 await p.getByRole('button',{name:'＋ 新对话'}).click()
 await p.locator('#agent-input').fill(prompt)
 await p.locator('#agent-input').press('Enter')
 const msg=p.locator('.agent-message.assistant').last()
 await p.waitForFunction(()=>document.querySelector('.agent-message.assistant')&&!document.querySelector('.agent-send.waiting'),{},{timeout:130000})
 return msg
}
async function handle(p,prompt,title,confirm=true){
 const msg=await chat(p,prompt)
 const b=msg.getByRole('button',{name:'核对'+title})
 if(!await b.count())throw Error('未产生确认单 '+title+' '+(await msg.innerText()).slice(0,600))
 await b.click()
 const dlg=p.getByRole('dialog').last()
 await dlg.getByText(/预约|偏好|文章|原流水/).first().waitFor()
 if(!confirm){await dlg.getByRole('button',{name:'撤销此操作'}).click();await dlg.getByText('已撤销').waitFor()}
 else{
 await dlg.getByRole('button',{name:'确认'+title}).click()
 await dlg.getByRole('button',{name:'关闭',exact:true}).last().waitFor({timeout:8000})
 }
 const receipt=await dlg.innerText()
 if(confirm && !/已完成|预约已创建/.test(receipt))throw Error('确认失败 '+receipt)
 await dlg.getByRole('button',{name:'关闭',exact:true}).last().click()
 console.log('通过',title,confirm?'执行':'撤销')
 return receipt
}
async function proxy(){
 const p=await login('staff','19999000002',seedPassword)
 await handle(p,'请代客预约：客户周女士，手机号19899000006；'+day(4)+' 16:00 李老师的自然光个人写真。','创建预约')
}
async function pref(){
 const p=await login('customer','19899000002',seedPassword)
 await handle(p,'帮我记住：我的拍摄风格偏好是自然光。请准备保存这一条长期偏好。','记住我的偏好')
 const msg=await chat(p,'我之前明确保存了什么拍摄风格偏好？')
 assert.match(await msg.innerText(),/自然光/)
 await p.goto(base+'/customer/profile')
 await p.getByRole('button',{name:'编辑',exact:true}).click()
 await p.getByLabel('偏好内容').fill('棚拍')
 await p.getByRole('button',{name:'保存',exact:true}).click()
 await p.getByText('棚拍',{exact:true}).waitFor()
 await p.getByRole('button',{name:'删除',exact:true}).click()
 await p.getByRole('button',{name:'确认删除'}).click()
 await p.getByText(/还没有保存偏好/).waitFor()
 console.log('通过 新对话读取、个人中心修改及删除偏好')
}
async function admin(){
 const p=await login('admin','19999000001',seedPassword)
 await handle(p,'请新建并发布一篇仅员工可见的知识文章，标题为「灯架交接」，正文为「闭店时清点灯架」。','保存知识文章')
 await p.goto(base+'/admin/knowledge')
 await p.getByText('灯架交接',{exact:true}).first().waitFor()
 console.log('通过 管理员确认发布')
}
async function booking(){
 const c=await login('customer','19899000001',seedPassword)
 const desk=await login('staff','19999000002',seedPassword)
 const photo=await login('staff','19999000003',seedPassword)
 const receipt=await handle(c,'请给我预约'+day(0)+' '+(process.env.AGENT_E2E_TODAY_SLOT || '18:00')+'的自然光个人写真，摄影师为李老师，请选择查到的李老师实际ID，准备办理。','创建预约')
 const id=Number(receipt.match(/预约已创建，预约 #(\d+)/)[1])
 await handle(desk,'请给预约 #'+id+' 登记到店。','登记到店')
 await handle(photo,'请为预约 #'+id+' 开始拍摄。','开始拍摄')
 await handle(photo,'请为预约 #'+id+' 确认本次拍摄已正常结束，请准备完成拍摄。','完成拍摄')
 await handle(desk,'请为预约 #'+id+' 登记微信收款399元。','登记收款')
 await handle(desk,'请退回预约 #'+id+' 的微信原收款9元，原因是服务优惠。请查实际原流水。','登记退款')
 await c.goto(base+'/customer/appointments/'+id)
 await c.getByText(/已完成/).first().waitFor()
 console.log('通过 客户查看拍摄状态')
}
async function requests(){
 const c=await login('customer','19899000003',seedPassword)
 const desk=await login('staff','19999000005',seedPassword)
 const receipt=await handle(c,'帮我约'+day(5)+'下午16:00，李老师的自然光个人写真。','创建预约')
 const id=Number(receipt.match(/预约已创建，预约 #(\d+)/)[1])
 await handle(c,'给我的预约 #'+id+' 提交改期申请，希望改到'+day(6)+'下午16点，因为行程冲突。','提交改期申请')
 await handle(desk,'查预约 #'+id+' 的待处理申请，同意改期到'+day(6)+'下午16:00，摄影师仍为李老师。','处理客户申请')
 await handle(c,'请为预约 #'+id+' 提交取消申请，因为出差。','提交取消申请')
 await handle(desk,'请同意预约 #'+id+' 目前待处理的取消申请。','处理客户申请')
 await c.goto(base+'/customer/appointments/'+id);await c.getByText(/已取消/).first().waitFor()
 await c.goto(base+'/customer/messages');await c.getByText(/申请处理进展/).first().waitFor()
 console.log('通过 改期取消状态与通知')
}
async function safety(){
 const p=await login('customer','19899000002',seedPassword)
 const other=await login('customer','19899000003',seedPassword)
 const msg=await chat(p,'请明确记住我的长期偏好：用光喜欢侧光。')
 const b=msg.getByRole('button',{name:'核对记住我的偏好'})
 const [resp]=await Promise.all([p.waitForResponse(r=>r.url().includes('/assistant/actions/')&&r.request().method()==='GET'),b.click()])
 const url=resp.url()
 const ownHeaders=resp.request().headers()
 async function hdr(page){const [r]=await Promise.all([page.waitForRequest(q=>q.url().includes('/api/v1/customer/preferences')),page.goto(base+'/customer/profile')]);return r.headers()}
 const headers=await hdr(other)
 const otherResult=await other.request.get(url,{headers})
 assert.equal(otherResult.status(),404)
 const before=await p.request.get(base+'/api/v1/customer/preferences',{headers:ownHeaders});assert(!JSON.stringify(await before.json()).includes('侧光'))
 const dlg=p.getByRole('dialog').last()
 const [post]=await Promise.all([p.waitForRequest(q=>q.url()===url+'/confirm'),dlg.getByRole('button',{name:'确认记住我的偏好'}).click()])
 await dlg.getByText(/已完成/).waitFor()
 const again=await p.request.post(post.url(),{headers:post.headers()})
 assert.equal((await again.json()).data.state,'DONE')
 const entries=await p.request.get(base+'/api/v1/customer/preferences',{headers:ownHeaders})
 assert.equal((await entries.json()).data.filter(x=>x.preference.includes('侧光')).length,1)
 const others=await other.request.get(base+'/api/v1/customer/preferences',{headers})
 assert(!JSON.stringify(await others.json()).includes('侧光'))
 await dlg.getByRole('button',{name:'关闭',exact:true}).last().click()
 await handle(p,'帮我记住：新的服装偏好是灰色。','记住我的偏好',false)
 console.log('通过 未确认无写入、跨账号拒绝、确认重放、撤销确认单')
}
;(async()=>{
 browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_BIN,args:['--disable-background-networking']})
 const cases={pref,admin,booking,requests,safety,proxy}
 const results=await (async()=>{const results=[]; for(const k of (process.argv.slice(2).length?process.argv.slice(2):Object.keys(cases))){try{await cases[k]();results.push({status:"fulfilled"})}catch(e){console.error(k,e.message);results.push({status:"rejected"})}}return results})()
 await browser.close()
 process.exitCode=results.some(r=>r.status==='rejected')?1:0
})().catch(e=>{console.error(e.message);process.exitCode=1})
