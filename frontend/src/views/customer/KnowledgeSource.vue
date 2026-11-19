<script setup>
import {ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import {get} from '../../api/operationsApi'
import {useChangeSync} from '../../composables/useChangeSync'
const route=useRoute()
const article=ref(null), error=ref('')

async function readSource(){
  const portal=route.path.split('/')[1]
  const {id,version}=route.params
  return get((portal==='customer'?'':'/'+portal)+'/knowledge/source/'+id+'/'+version)
}
function show(value){
  article.value=value
  error.value=''
}
function failed(e){
  article.value=null
  error.value=e.response?.status===404?'这份资料已停用或当前账号无法查看。':'暂时无法读取资料。'
}
watch(()=>route.fullPath,async(_,previous,onCleanup)=>{
  let active=true
  onCleanup(()=>{active=false})
  article.value=null
  error.value=''
  try{
    const data=await readSource()
    if(active)show(data)
  }catch(e){if(active)failed(e)}
},{immediate:true})
useChangeSync({fetchData:readSource,current:()=>article.value,apply:show,onError:failed,
  key:()=>route.fullPath,publicCatalog:route.path.startsWith('/customer/')})
</script>
<template><section class="panel"><p v-if="error" role="alert">{{error}}</p><template v-if="article"><h1>{{article.title}}</h1><p class="subtle">引用版本 {{article.version}}</p><p class="source-body">{{article.content}}</p></template></section></template>
<style scoped>.source-body{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.9;margin-top:20px}</style>
