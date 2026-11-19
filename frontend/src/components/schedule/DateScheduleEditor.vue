<script setup>
import { ref, watch } from 'vue'
import { getSchedule, saveSchedule, restoreWeeklySchedule } from '../../api/bookingApi'
import { today } from '../../utils/bookingFormat'
import { useChangeSync } from '../../composables/useChangeSync'
const props = defineProps({ kind: String, resourceId: [String, Number] })
const date=ref(today()), form=ref(null), busy=ref(false), loading=ref(false), error=ref(''), message=ref(''), baseline=ref('')
let sequence=0
function apply(v){form.value=v;baseline.value=JSON.stringify(v)}
async function load(){const seq=++sequence;loading.value=true;form.value=null;error.value='';message.value='';try{const v=await getSchedule(props.kind,{resourceId:props.resourceId,date:date.value});if(seq===sequence)apply(v)}catch(e){if(seq===sequence)error.value=e.userMessage}finally{if(seq===sequence)loading.value=false}}
watch(()=>[props.kind,props.resourceId,date.value],load,{immediate:true})
async function save(restore=false){busy.value=true;error.value='';message.value='';try{const params={resourceId:Number(props.resourceId),date:date.value};apply(restore?await restoreWeeklySchedule(props.kind,params):await saveSchedule(props.kind,{...params,enabled:form.value.enabled,periods:form.value.periods}));message.value=restore?'已恢复固定周安排':'当天特殊安排已保存'}catch(e){error.value=e.userMessage}finally{busy.value=false}}
useChangeSync({fetchData:()=>getSchedule(props.kind,{resourceId:props.resourceId,date:date.value}),current:()=>form.value,apply,key:()=>[props.kind,props.resourceId,date.value,sequence],paused:()=>busy.value||loading.value||!form.value||JSON.stringify(form.value)!==baseline.value})
</script>
<template>
  <section>
    <div class="section-title"><h2>特殊日期</h2><input aria-label="特殊安排日期" type="date" v-model="date" :min="today()" :disabled="busy" /></div>
    <p class="subtle">用于请假、调休或临时加班，仅影响所选日期。已有预约不受影响后才可保存。</p>
    <p v-if="loading">正在读取安排…</p><p v-if="error" class="error-message" role="alert">{{ error }}</p><p v-if="message" class="success-message">{{ message }}</p>
    <form v-if="form" @submit.prevent="save(false)"><fieldset :disabled="busy||loading" class="schedule-fields">
      <p class="subtle">当前来源：{{ form.source==='DATE'?'特殊日期安排':form.source==='WEEKLY'?'固定周安排':'尚未安排' }}</p>
      <label class="check-label"><input type="checkbox" v-model="form.enabled" />当天工作</label>
      <div v-if="form.enabled"><div class="period-row" v-for="(p,i) in form.periods" :key="i"><input aria-label="开始时间" type="time" v-model="p.startTime" required /><span>至</span><input aria-label="结束时间" type="time" v-model="p.endTime" required /><button type="button" class="text-button" @click="form.periods.splice(i,1)">移除</button></div>
      <button type="button" class="outline-button" :disabled="form.periods.length>=8" @click="form.periods.push({startTime:'09:00',endTime:'18:00'})">＋ 添加时段</button></div>
      <div class="schedule-actions"><button class="primary-button">保存当天安排</button><button v-if="form.source==='DATE'" type="button" class="outline-button" @click="save(true)">恢复固定周安排</button></div>
    </fieldset></form>
  </section>
</template>
<style scoped>.schedule-actions{display:flex;gap:12px;flex-wrap:wrap;margin-top:20px}</style>
