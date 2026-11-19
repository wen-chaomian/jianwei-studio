<script setup>
import {ref,onMounted} from 'vue'
import {get} from '../../api/operationsApi'
import {useChangeSync} from '../../composables/useChangeSync'
const data=ref(null),page=ref(1),error=ref('')
async function fetchData(){const [rules,articles]=await Promise.all([get('/booking-rule'),get('/knowledge',{page:page.value,size:10})]);return {rules,articles}}
async function load(){try{data.value=await fetchData();error.value=''}catch(e){error.value=e.userMessage}}
async function turn(n){page.value=n;await load()}
onMounted(load)
useChangeSync({fetchData,current:()=>data.value,apply:v=>{data.value=v;error.value=''},key:()=>page.value,publicCatalog:true,onError:e=>error.value=e.userMessage})
const time=v=>v?.slice(0,5)
</script>
<template><div class="page-heading"><div><span class="eyebrow">BEFORE YOUR VISIT</span><h1>预约须知</h1><p>提前了解安排，安心享受拍摄。</p></div></div><p class="error-message" role="alert" v-if="error">{{error}}</p><template v-if="data"><section class="panel"><h2>预约规则</h2><dl class="notice-rules"><div><dt>营业时间</dt><dd>{{time(data.rules.openTime)}}–{{time(data.rules.closeTime)}}</dd></div><div><dt>提前预约</dt><dd>至少提前 {{data.rules.minAdvanceHours}} 小时，最多提前 {{data.rules.maxAdvanceDays}} 天</dd></div><div><dt>预约时间间隔</dt><dd>{{data.rules.slotMinutes}} 分钟</dd></div><div><dt>自助改期与取消</dt><dd>请在预约开始前 {{data.rules.changeCutoffHours}} 小时办理，超出自助办理期限请提交申请。</dd></div></dl><p class="subtle">可预约时间以套餐页面显示的摄影师和场地空档为准。</p></section><section class="panel" v-if="data.articles.items.length"><h2>服务说明</h2><article v-for="a in data.articles.items" :key="a.id" :id="'article-'+a.id" class="notice-article"><h3>{{a.title}}</h3><p>{{a.content}}</p></article><div class="pagination" v-if="data.articles.total>10"><button class="outline-button" :disabled="page===1" @click="turn(page-1)">上一页</button><span>{{page}}</span><button class="outline-button" :disabled="page*10>=data.articles.total" @click="turn(page+1)">下一页</button></div></section></template></template>
<style scoped>.notice-rules{display:grid;gap:20px;margin:24px 0}.notice-rules>div{display:grid;grid-template-columns:150px 1fr;gap:20px}.notice-rules dt{color:var(--sub)}.notice-rules dd{margin:0;line-height:1.8}.notice-article{border-top:1px solid var(--line);padding:24px 0}.notice-article:first-of-type{margin-top:20px}.notice-article h3{margin:8px 0 16px}.notice-article p{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.9}@media(max-width:600px){.notice-rules>div{grid-template-columns:1fr;gap:4px}}</style>
