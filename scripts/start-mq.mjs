import {spawn,spawnSync} from 'node:child_process';
import {existsSync,openSync,writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import net from 'node:net';
const root=fileURLToPath(new URL('../',import.meta.url));
const base=root+'.local/rocketmq',home=base+'/rocketmq-all-5.3.2-bin-release';
const java=process.env.JAVA_HOME ? process.env.JAVA_HOME+'/bin/java' : 'java';
if(!existsSync(home+'/lib')) throw Error('请先安装 RocketMQ 5.3.2 至 .local/rocketmq');
const common=['--add-opens=java.base/java.lang=ALL-UNNAMED','--add-opens=java.base/java.nio=ALL-UNNAMED','--add-opens=java.base/sun.nio.ch=ALL-UNNAMED',`-Drocketmq.home.dir=${home}`,`-Duser.home=${base}`,'-cp',home+'/conf:'+home+'/lib/*'];
function listening(port){return new Promise(resolve=>{const s=net.connect({host:'127.0.0.1',port});s.once('connect',()=>{s.destroy();resolve(true)});s.once('error',()=>resolve(false));});}
async function start(name,port,args,heap){
 if(await listening(port)){console.log(`${name} 已运行：${port}`);return;}
 const fd=openSync(`${base}/logs/${name}.log`,'a');
 const p=spawn(java,[`-Xms128m`,`-Xmx${heap}m`,...common,...args],{cwd:root,detached:true,stdio:['ignore',fd,fd]});p.unref();writeFileSync(`${base}/${name}.pid`,String(p.pid));
 for(let i=0;i<90;i++){if(await listening(port)){console.log(`${name} 已就绪：${port}`);return;}await new Promise(r=>setTimeout(r,500));}
 throw Error(`${name} 启动失败，请查看 .local/rocketmq/logs/${name}.log`);
}
await start('nameserver',9876,['org.apache.rocketmq.namesrv.NamesrvStartup','-c',base+'/namesrv.properties'],256);
await start('broker',8081,['org.apache.rocketmq.proxy.ProxyStartup','-pm','LOCAL','-bc',base+'/broker.properties','-pc',base+'/proxy.json'],512);
for(const topic of ['JianweiAgentCommand','JianweiAgentResult']){
 const p=spawnSync(java,['-Xmx128m',...common,'org.apache.rocketmq.tools.command.MQAdminStartup','updateTopic','-n','127.0.0.1:9876','-b','127.0.0.1:10911','-t',topic,'-r','4','-w','4'],{cwd:root,encoding:'utf8'});
 if(p.status!==0)throw Error(p.stderr||p.stdout);
}
console.log('RocketMQ 已就绪。可以在 IDEA 和 PyCharm 启动服务。');
