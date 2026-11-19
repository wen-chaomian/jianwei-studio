<script setup>
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { get, patch, post } from '../../api/operationsApi'

const emit = defineEmits(['close'])
const base = '/admin/knowledge/config'
const saved = ref(null), editing = ref(false), key = ref(''), model = ref('')
const region = ref('cn-beijing'), workspace = ref(''), models = ref([])
const busy = ref(false), message = ref(''), error = ref('')
const connectionChanged = computed(() => region.value !== saved.value?.region ||
  workspace.value.trim() !== (saved.value?.workspace || ''))
let timer, mounted = true

async function perform(action) {
  if (busy.value) return
  busy.value = true
  message.value = error.value = ''
  try { await action() }
  catch (e) { error.value = e.userMessage || '操作失败，请重试' }
  finally { busy.value = false }
}

function sync(config) {
  saved.value = config
  region.value = config.region
  workspace.value = config.workspace || ''
  model.value = config.pending_model || config.model
  editing.value = !config.configured
  key.value = ''
  clearTimeout(timer)
  if (mounted && config.pending_model) timer = setTimeout(checkRebuild, 3000)
}
async function checkRebuild() {
  if (!mounted) return
  try {
    const latest = await get(base)
    if (!mounted) return
    if (!latest.pending_model) {
      sync(latest)
      message.value = '新索引已就绪，模型已切换'
      return
    }
    saved.value = latest
  } catch { /* The next check resumes after a temporary disconnect. */ }
  if (mounted) timer = setTimeout(checkRebuild, 3000)
}
function load(notify = true) {
  return perform(async () => {
    sync(await get(base))
    models.value = []
    if (notify) message.value = '已重新加载'
  })
}
function loadModels() {
  return perform(async () => {
    models.value = (await get(base + '/models')).items
    message.value = models.value.length ? '模型列表已更新' : '此空间未返回兼容的文本向量模型'
  })
}
function copyKey() {
  return perform(async () => {
    const result = await post(base + '/key')
    try { await navigator.clipboard.writeText(result.api_key); message.value = '已复制' }
    finally { result.api_key = '' }
  })
}
function save() {
  return perform(async () => {
    sync(await patch(base, { region: region.value, workspace: workspace.value.trim(),
      api_key: editing.value ? key.value.trim() || null : null, model: model.value, version: saved.value.version }))
    models.value = []
    message.value = '已保存'
  })
}
onMounted(() => load(false))
onBeforeUnmount(() => { mounted = false; clearTimeout(timer) })
</script>
<template>
  <form v-if="saved" @submit.prevent="save">
    <div class="model-key-label">
      <label for="embedding-key">API Key</label>
      <button v-if="saved.configured" type="button" class="model-text-action" :disabled="busy || !!saved.pending_model"
        @click="editing = !editing; key = ''">{{ editing ? '取消更换' : '更换密钥' }}</button>
    </div>
    <div class="model-key-field">
      <input v-if="editing" id="embedding-key" type="password" v-model="key" autocomplete="new-password"
        maxlength="512" placeholder="输入百炼密钥" :disabled="busy" />
      <input v-else id="embedding-key" readonly :value="saved.key_masked" aria-label="已保存的百炼密钥（脱敏）" />
      <button v-if="saved.configured && !editing" type="button" class="model-copy-action" :disabled="busy"
        aria-label="复制密钥" title="复制密钥" @click="copyKey">⧉</button>
    </div>
    <label class="model-select-label" for="embedding-model">模型</label>
    <div class="model-select-row">
      <select id="embedding-model" v-model="model" :disabled="busy || !!saved.pending_model">
        <option v-if="!models.some(m => m.id === model)" :value="model">{{ model }}</option>
        <option v-for="item in models" :key="item.id" :value="item.id">{{ item.id }}</option>
      </select>
      <button type="button" :disabled="busy || editing || connectionChanged || !!saved.pending_model" @click="loadModels">↻ 获取模型</button>
    </div>
    <details class="connection" :open="!saved.configured">
      <summary>百炼连接设置</summary>
      <label for="embedding-region">服务地域</label>
      <select id="embedding-region" v-model="region" :disabled="busy || !!saved.pending_model">
        <option value="cn-beijing">北京</option><option value="ap-southeast-1">新加坡</option>
      </select>
      <label for="embedding-workspace">业务空间 ID</label>
      <input id="embedding-workspace" v-model="workspace" :disabled="busy || !!saved.pending_model"
        maxlength="63" pattern="ws-[a-z0-9\-]+" placeholder="使用专属域名时填写" />
    </details>
    <div class="model-feedback" aria-live="polite">
      <span v-if="error" class="agent-error" role="alert">{{ error }}</span>
      <span v-else-if="saved.pending_model">{{ saved.rebuild_error || '正在重建索引' }}，当前仍使用 {{ saved.model }}。</span>
      <span v-else>{{ message || (editing || connectionChanged ? '保存连接配置后可获取模型' : '') }}</span>
    </div>
    <footer class="model-actions">
      <button type="button" class="model-reload" :disabled="busy" @click="load()">↶ 重新加载</button>
      <div><button type="button" :disabled="busy" @click="emit('close')">取消</button>
        <button class="model-save" :disabled="busy || !!saved.pending_model || (editing && !key.trim())">{{ busy ? '处理中…' : '保存' }}</button></div>
    </footer>
  </form>
  <p v-else-if="error" class="agent-error" role="alert">{{ error }} <button @click="load(false)">重试</button></p>
</template>
<style scoped>
.connection {margin-top:12px;color:#526972;font-size:13px}
.connection summary {cursor:pointer}
.connection label {margin:10px 0 5px}
.connection input,.connection select {width:100%;height:38px;padding:0 10px}
</style>
