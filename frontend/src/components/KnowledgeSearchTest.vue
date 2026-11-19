<script setup>
import {ref} from 'vue'
import {post} from '../api/operationsApi'
const question=ref(''),result=ref(null),busy=ref(false),error=ref('')
async function search(){
 busy.value=true
 result.value=null
 error.value=''
 try{result.value=await post('/admin/knowledge/search',{question:question.value.trim()})}
 catch(e){error.value=e.userMessage}
 finally{busy.value=false}
}
</script>
<template><details class="panel"><summary>检索测试</summary><form @submit.prevent="search" class="booking-form"><label class="full">客户问题<input maxlength="500" required v-model="question" /></label><button class="outline-button" :disabled="busy||!question.trim()">检索</button></form><p role="alert" v-if="error">{{error}}</p><template v-if="result"><p v-if="!result.items.length">没有找到相关的可检索内容。</p><article v-for="(hit,i) in result.items" :key="i"><RouterLink :to="hit.url">{{hit.title}} · 第 {{hit.version}} 版</RouterLink><p>{{hit.passage}}</p></article></template></details></template>
