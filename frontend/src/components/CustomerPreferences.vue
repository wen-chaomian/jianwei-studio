<script setup>
import { onMounted,ref } from 'vue'
import { get,patch,post } from '../api/operationsApi'
const items=ref([]),error=ref(''),editing=ref(null),value=ref(''),busy=ref(false),removeTopic=ref(null)
async function load(){items.value=await get('/customer/preferences')}
async function perform(fn){
  if(busy.value)return
  busy.value=true;error.value=''
  try {await fn();await load();editing.value=removeTopic.value=null}
  catch(e){error.value=e.userMessage}
  finally{busy.value=false}
}
onMounted(async()=>{try{await load()}catch(e){error.value=e.userMessage}})
</script>
<template>
  <section class="panel preferences">
    <h2>我的偏好</h2>
    <p v-if="error" role="alert" class="error-message">{{error}}</p>
    <p v-if="!items.length && !error" class="subtle">还没有保存偏好。可以对助手说「帮我记住，我喜欢自然光」。</p>
    <article v-for="item in items" :key="item.topic">
      <strong>{{item.topic}}</strong>
      <template v-if="editing===item.topic">
        <input v-model="value" maxlength="300" aria-label="偏好内容" />
        <button :disabled="busy || !value.trim()" @click="perform(()=>patch('/customer/preferences',{...item,preference:value.trim()}))">保存</button>
        <button @click="editing=null">取消</button>
      </template>
      <template v-else>
        <span>{{item.preference}}</span>
        <button @click="editing=item.topic;value=item.preference;removeTopic=null">编辑</button>
        <button v-if="removeTopic!==item.topic" @click="removeTopic=item.topic">删除</button>
        <template v-else><button :disabled="busy" @click="perform(()=>post('/customer/preferences/remove',item))">确认删除</button><button @click="removeTopic=null">保留</button></template>
      </template>
    </article>
  </section>
</template>
<style scoped>.preferences{margin-top:16px}.preferences article{display:flex;align-items:center;gap:12px;flex-wrap:wrap;padding:12px 0}.preferences article span,.preferences article input{flex:1;min-width:8em;overflow-wrap:anywhere}.preferences button{color:var(--primary);background:transparent;border:0;cursor:pointer}</style>
