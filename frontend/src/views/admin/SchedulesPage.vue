<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import * as api from '../../api/bookingApi'
import { useChangeSync } from '../../composables/useChangeSync'
import DateScheduleEditor from '../../components/schedule/DateScheduleEditor.vue'
const kind=ref('photographer'),id=ref(''),tab=ref('weekly'),photographers=ref([]),venues=ref([]),days=ref([]),busy=ref(false),loading=ref(false),error=ref(''),message=ref(''),baseline=ref('')
const names=['周一','周二','周三','周四','周五','周六','周日']
const resources=computed(()=>kind.value==='photographer'?photographers.value:venues.value)
let sequence=0
function apply(v){days.value=v;baseline.value=JSON.stringify(v)}
async function load(){const seq=++sequence;error.value='';message.value='';days.value=[];if(!id.value){loading.value=false;return}loading.value=true;try{const v=await api.getWeeklySchedule(kind.value,id.value);if(seq===sequence)apply(v)}catch(e){if(seq===sequence)error.value=e.userMessage}finally{if(seq===sequence)loading.value=false}}
watch(id,load);watch(kind,()=>{id.value=resources.value[0]?.id||'';load()})
onMounted(async()=>{try{[photographers.value,venues.value]=await Promise.all([api.getPhotographers(),api.getVenues()]);id.value=resources.value[0]?.id||''}catch(e){error.value=e.userMessage}})
function toggle(d){if(d.enabled&&!d.periods.length)d.periods=[{startTime:'09:00',endTime:'12:00'},{startTime:'13:00',endTime:'18:00'}]}
function copy(d){for(const target of days.value)if(target.enabled&&target!==d)target.periods=structuredClone(d.periods.map(p=>({...p})));message.value='时段已复制到其他工作日，保存后生效'}
async function save(){busy.value=true;error.value='';message.value='';try{apply(await api.saveWeeklySchedule(kind.value,{resourceId:Number(id.value),days:days.value}));message.value='固定周排班已保存，今后每周自动沿用'}catch(e){error.value=e.userMessage}finally{busy.value=false}}
useChangeSync({fetchData:()=>api.getWeeklySchedule(kind.value,id.value),current:()=>days.value,apply,key:()=>[kind.value,id.value,sequence],paused:()=>!id.value||busy.value||loading.value||JSON.stringify(days.value)!==baseline.value})
</script>
<template>
  <div class="page-heading"><div><span class="eyebrow">AVAILABILITY PLANNING</span><h1>排班管理</h1><p>每周固定安排，特殊日期单独调整。</p></div></div>
  <section class="panel">
    <div class="booking-form"><label>资源类型<select v-model="kind" :disabled="busy"><option value="photographer">摄影师</option><option value="venue">场地</option></select></label><label>资源<select v-model="id" :disabled="busy"><option value="" disabled>请选择</option><option v-for="r in resources" :key="r.id" :value="r.id">{{ r.name }}</option></select></label></div>
    <div class="schedule-tabs"><button type="button" :class="tab==='weekly'?'primary-button':'outline-button'" @click="tab='weekly'">固定周安排</button><button type="button" :class="tab==='date'?'primary-button':'outline-button'" @click="tab='date'">特殊日期</button></div>
    <p v-if="error" role="alert" class="error-message">{{ error }}</p><p v-if="message" class="success-message">{{ message }}</p>
    <template v-if="tab==='weekly'"><p class="subtle">保存一次，每周沿用。休息日不开放预约；特殊日期优先于周安排。拍摄和缓冲时间均须落在班次内。</p><p v-if="loading">正在读取排班…</p>
    <form v-if="id&&days.length" @submit.prevent="save"><fieldset :disabled="busy||loading" class="schedule-fields">
      <div v-for="d in days" :key="d.dayOfWeek" class="week-row">
        <div class="week-label"><strong>{{ names[d.dayOfWeek-1] }}</strong><label class="check-label"><input type="checkbox" v-model="d.enabled" @change="toggle(d)" />工作</label></div>
        <div v-if="d.enabled" class="week-periods"><div v-for="(p,i) in d.periods" :key="i" class="period-row"><input :aria-label="names[d.dayOfWeek-1]+'开始时间'+(i+1)" type="time" v-model="p.startTime" required /><span>至</span><input :aria-label="names[d.dayOfWeek-1]+'结束时间'+(i+1)" type="time" v-model="p.endTime" required /><button class="text-button" type="button" @click="d.periods.splice(i,1)">移除</button></div>
        <div class="week-actions"><button class="text-button" type="button" :disabled="d.periods.length>=8" @click="d.periods.push({startTime:'09:00',endTime:'18:00'})">＋ 添加时段</button><button class="text-button" type="button" :disabled="!d.periods.length" @click="copy(d)">复制到其他工作日</button></div></div>
        <p v-else class="subtle">休息</p>
      </div><button class="primary-button weekly-save">{{ busy?'保存中…':'保存周排班' }}</button>
    </fieldset></form></template>
    <DateScheduleEditor v-else-if="id" :key="kind+id" :kind="kind" :resource-id="id" />
    <p v-if="!id&&!loading" class="empty-state">暂无资源，请先在套餐与资源中配置。</p>
  </section>
</template>
<style scoped>
.schedule-tabs{display:flex;gap:10px;margin:24px 0;flex-wrap:wrap}.week-row{display:grid;grid-template-columns:140px 1fr;gap:20px;padding:18px 0;border-bottom:1px solid var(--line,#d9e5e8)}.week-label{display:flex;align-items:flex-start;gap:18px;padding-top:10px}.week-label .check-label{margin:0}.week-periods .period-row{margin-top:0;margin-bottom:8px;justify-content:flex-start}.week-actions{display:flex;gap:22px;flex-wrap:wrap}.weekly-save{margin-top:24px}@media(max-width:650px){.week-row{grid-template-columns:1fr;gap:8px}.period-row{flex-wrap:wrap}.period-row input{min-width:105px}}
</style>
