<script setup>
import KnowledgeSearchTest from '../../components/KnowledgeSearchTest.vue'
import {ref,onMounted} from 'vue'
import {get,post,patch} from '../../api/operationsApi'
import {useChangeSync} from '../../composables/useChangeSync'
const rows=ref({items:[],total:0}),page=ref(1),form=ref(null),busy=ref(false),error=ref(''),notice=ref('')
const labels={DRAFT:'草稿',PUBLISHED:'已发布',DISABLED:'已停用'}
async function load(){try{rows.value=await get('/admin/knowledge',{page:page.value,size:10})}catch(e){error.value=e.userMessage}}
function edit(a){error.value='';notice.value='';form.value=a?{...a}:{title:'',category:'预约须知',content:'',status:'DRAFT',audience:'PUBLIC',version:0}}
async function save(event){busy.value=true;error.value='';notice.value='';try{const a={...form.value,status:event.submitter?.value||form.value.status};if(a.id)await patch('/admin/knowledge/'+a.id,a);else await post('/admin/knowledge',a);notice.value=a.status==='PUBLISHED'?'已发布，客户可查看最新内容。':a.status==='DISABLED'?'已停用，客户页面不再展示。':'草稿已保存。';form.value=null;await load()}catch(e){error.value=e.userMessage}finally{busy.value=false}}
const stages={QUEUED:'待同步',PROCESSING:'同步中',WAIT_CONFIG:'等待百炼配置',READY:'可检索',FAILED:'同步失败',OFF:'未参与检索'}
async function retry(a){
 busy.value=true
 try{await post('/admin/knowledge/'+a.id+'/retry',{});await load()}
 catch(e){error.value=e.userMessage}
 finally{busy.value=false}
}
async function turn(n){page.value=n;await load()}
onMounted(load)
useChangeSync({fetchData:()=>get('/admin/knowledge',{page:page.value,size:10}),current:()=>rows.value,apply:v=>rows.value=v,key:()=>page.value,paused:()=>busy.value||!!form.value})
</script>
<template>
  <div class="page-heading">
    <h1>知识库</h1>
    <button v-if="!form" class="primary-button" @click="edit(null)">新增内容</button>
  </div>
  <p class="error-message" role="alert" v-if="error">{{ error }}</p>
  <p class="success-message" role="status" v-if="notice">{{ notice }}</p>
  <section class="panel" v-if="form">
    <h2>{{ form.id ? '编辑内容' : '新增内容' }}</h2>
    <form @submit.prevent="save">
      <div class="booking-form">
        <label class="full">标题<input v-model="form.title" maxlength="120" required :disabled="busy" /></label>
        <label class="full">正文<textarea v-model="form.content" rows="7" maxlength="12000" required :disabled="busy" /></label>
      </div>
      <details :open="form.audience !== 'PUBLIC'"><summary>可见范围 · {{ {PUBLIC:'三端可见',STAFF:'员工和管理端',ADMIN:'仅管理端'}[form.audience] }}</summary>
        <label>谁可以查看 <select v-model="form.audience" :disabled="busy"><option value="PUBLIC">三端可见</option><option value="STAFF">员工和管理端</option><option value="ADMIN">仅管理端</option></select></label>
      </details>
      <div class="form-actions">
        <button type="submit" value="PUBLISHED" class="primary-button" :disabled="busy">{{ form.status === 'PUBLISHED' ? '保存修改' : form.status === 'DISABLED' ? '恢复发布' : '发布' }}</button>
        <button v-if="form.status !== 'PUBLISHED'" type="submit" :value="form.status" class="outline-button" :disabled="busy">{{ form.status === 'DRAFT' ? '保存草稿' : '保存修改' }}</button>
        <button v-else type="submit" value="DISABLED" class="outline-button" :disabled="busy">停用</button>
        <button type="button" class="text-button" :disabled="busy" @click="form=null">取消</button>
      </div>
    </form>
  </section>
  <section class="panel" v-if="!form">
    <p v-if="!rows.items.length" class="subtle">暂无内容</p>
    <div class="resource-list">
      <article v-for="a in rows.items" :key="a.id">
        <div><h3>{{ a.title }}</h3><p>{{ labels[a.status] }} · {{ stages[a.indexState] }}</p><p class="excerpt">{{ a.content }}</p></div>
        <span v-if="a.indexReason" class="subtle">{{a.indexReason}}</span>
        <button v-if="['FAILED','WAIT_CONFIG'].includes(a.indexState)" class="outline-button" :disabled="busy" @click="retry(a)">重试</button>
        <button class="outline-button" @click="edit(a)">编辑</button>
      </article>
    </div>
    <div class="pagination" v-if="rows.total>10">
      <span>共 {{ rows.total }} 条</span>
      <button class="outline-button" :disabled="page===1" @click="turn(page-1)">上一页</button>
      <span>{{ page }}</span>
      <button class="outline-button" :disabled="page*10>=rows.total" @click="turn(page+1)">下一页</button>
    </div>
  </section>
  <KnowledgeSearchTest v-if="!form" />
</template>
<style scoped>.excerpt{white-space:pre-wrap;overflow:hidden;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow-wrap:anywhere}</style>
