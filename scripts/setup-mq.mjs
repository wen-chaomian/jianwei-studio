// Local macOS/Linux development setup. Runtime data and credentials stay under .local / .env.
import {mkdirSync,existsSync,readFileSync,writeFileSync,chmodSync} from 'node:fs';
import {createHash,randomBytes} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
const base=root+'.local/rocketmq',version='5.3.2',folder=`rocketmq-all-${version}-bin-release`;
mkdirSync(base+'/logs',{recursive:true});mkdirSync(base+'/data',{recursive:true});
const archive=base+'/download.zip';
const expected='a2a215c66f3b4c8d5e54a9a769b387c07d219e800c7ed8e9627d990644d8ebf9c35914405cab701a8bc46ffa03ad0f06c7737b1a6ce7b731dd2ad3c90eada972';
if(!existsSync(base+'/'+folder+'/lib')){
 if(!existsSync(archive)){
  const downloaded=spawnSync('curl',['-fL','--retry','2',`https://mirrors.aliyun.com/apache/rocketmq/${version}/${folder}.zip`,'-o',archive],{stdio:'inherit'});
  if(downloaded.status!==0)throw Error('RocketMQ 下载失败');
 }
 if(createHash('sha512').update(readFileSync(archive)).digest('hex')!==expected)throw Error('RocketMQ SHA-512 与 Apache 官方校验值不符');
 if(spawnSync('unzip',['-q',archive,'-d',base],{stdio:'inherit'}).status!==0)throw Error('解压失败');
}
if(existsSync(base+'/broker.properties') || existsSync(base+'/proxy.json')){
 console.log('已有 MQ 配置，保持不变。启动：node scripts/start-mq.mjs');process.exit(0);
}
const envPath=root+'agent-service/.env';
if(!existsSync(envPath))throw Error('请先配置 agent-service/.env');
const username='worker-'+randomBytes(8).toString('hex'),password=randomBytes(32).toString('hex');
const init=JSON.stringify({username,password});
writeFileSync(base+'/namesrv.properties','listenPort=9876\nbindAddress=127.0.0.1\n');
writeFileSync(base+'/broker.properties',`brokerClusterName=JianweiLocal
brokerName=jianwei-local
brokerId=0
brokerIP1=127.0.0.1
brokerIP2=127.0.0.1
bindAddress=127.0.0.1
listenPort=10911
namesrvAddr=127.0.0.1:9876
brokerRole=ASYNC_MASTER
flushDiskType=SYNC_FLUSH
autoCreateTopicEnable=false
autoCreateSubscriptionGroup=true
storePathRootDir=${base}/data
storePathCommitLog=${base}/data/commitlog
mappedFileSizeCommitLog=67108864
mapedFileSizeCommitLog=67108864
maxMessageSize=1048576
fileReservedTime=24
authenticationMetadataProvider=org.apache.rocketmq.auth.authentication.provider.LocalAuthenticationMetadataProvider
authConfigPath=${base}/auth
initAuthenticationUser=${init}
`,{mode:0o600});
writeFileSync(base+'/proxy.json',JSON.stringify({rocketMQClusterName:'JianweiLocal',namesrvAddr:'127.0.0.1:9876',grpcServerPort:8081,remotingListenPort:8082,localServeAddr:'127.0.0.1',proxyAddr:'127.0.0.1',authenticationEnabled:true,authenticationMetadataProvider:'org.apache.rocketmq.proxy.auth.ProxyAuthenticationMetadataProvider',authConfigPath:base+'/auth',initAuthenticationUser:init},null,2),{mode:0o600});
let env=readFileSync(envPath,'utf8').replace(/^AGENT_MQ_(ACCESS_KEY|SECRET_KEY|AUTOSTART)=.*\n?/gm,'');
env+=`\nAGENT_MQ_ACCESS_KEY=${username}\nAGENT_MQ_SECRET_KEY=${password}\nAGENT_MQ_AUTOSTART=true\n`;
writeFileSync(envPath,env);chmodSync(envPath,0o600);
console.log('MQ 已安装，凭据已写入本机配置。启动：node scripts/start-mq.mjs');
