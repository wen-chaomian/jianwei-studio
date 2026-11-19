import {spawnSync} from 'node:child_process'
import {cpSync,mkdirSync,rmSync} from 'node:fs'
import {fileURLToPath} from 'node:url'
import path from 'node:path'
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..')
function run(command,args,cwd){const result=spawnSync(command,args,{cwd,stdio:'inherit',env:process.env});if(result.error)throw result.error;if(result.status!==0)process.exit(result.status||1)}
run(process.platform==='win32'?'npm.cmd':'npm',['run','build'],path.join(root,'frontend'))
const staticDir=path.join(root,'backend/src/main/resources/static');rmSync(staticDir,{recursive:true,force:true});mkdirSync(staticDir,{recursive:true});cpSync(path.join(root,'frontend/dist'),staticDir,{recursive:true})
const maven=process.env.MAVEN_CMD || (process.platform==='win32'?'mvn.cmd':'mvn')
run(maven,['-q','clean','verify'],path.join(root,'backend'))
mkdirSync(path.join(root,'release'),{recursive:true});cpSync(path.join(root,'backend/target/studio-0.1.0.jar'),path.join(root,'release/studio.jar'))
console.log('构建完成：release/studio.jar。Java 启动后通过 8080 访问三个端。')
