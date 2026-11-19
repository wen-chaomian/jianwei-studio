<script setup>
import { ref, onMounted } from 'vue'
import { modelConfig, saveModelConfig, availableModels, copyModelKey } from '../../api/agentApi'
import EmbeddingSettings from './EmbeddingSettings.vue'
const tab=ref('chat')
const emit = defineEmits(['saved', 'close'])
const config = ref(null), key = ref(''), model = ref(''), models = ref([])
const busy = ref(false), editing = ref(false), message = ref(''), error = ref('')
async function perform(action) {
  if (busy.value) return
  busy.value = true; error.value = ''; message.value = ''
  try { await action() } catch (e) { error.value = e.userMessage || '操作失败，请重试' }
  finally { busy.value = false }
}
async function load(notify = true) {
  await perform(async () => {
    config.value = await modelConfig(); model.value = config.value.model
    key.value = ''; editing.value = !config.value.key_configured; models.value = []
    if (notify) message.value = '已重新加载'
  })
}
async function save() {
  await perform(async () => {
    config.value = await saveModelConfig({ api_key: editing.value ? key.value.trim() || null : null, model: model.value, version: config.value.version })
    key.value = ''; editing.value = !config.value.key_configured
    message.value = '已保存，即时生效'; emit('saved')
  })
}
async function loadModels() {
  await perform(async () => { models.value = (await availableModels()).items; message.value = '模型列表已更新' })
}
async function copy() {
  await perform(async () => {
    const result = await copyModelKey()
    try { await navigator.clipboard.writeText(result.api_key); message.value = '已复制' }
    finally { result.api_key = '' }
  })
}
function toggleKey() { editing.value = !editing.value; key.value = ''; message.value = ''; error.value = '' }
onMounted(() => load(false))
</script>
<template>
  <section class="agent-settings compact-model-settings">
    <nav class="config-tabs" aria-label="模型用途"><button type="button" :aria-pressed="tab==='chat'" @click="tab='chat'">语言模型配置</button><button type="button" :aria-pressed="tab==='embedding'" @click="tab='embedding'">向量模型配置</button></nav>
    <EmbeddingSettings v-if="tab==='embedding'" @close="emit('close')" />
    <form v-else-if="config" @submit.prevent="save">
      <div class="model-key-label"><label for="model-api-key">API Key</label><button v-if="config.key_configured" type="button" class="model-text-action" :disabled="busy" @click="toggleKey">{{ editing ? '取消更换' : '更换密钥' }}</button></div>
      <div class="model-key-field">
        <input id="model-api-key" v-if="editing" v-model="key" type="password" autocomplete="new-password" maxlength="512" placeholder="输入新密钥" :disabled="busy" />
        <input id="model-api-key" v-else :value="config.key_masked" readonly aria-label="已保存的密钥（脱敏）" />
        <button v-if="config.key_configured && !editing" type="button" class="model-copy-action" :disabled="busy" aria-label="复制密钥" title="复制密钥" @click="copy">⧉</button>
      </div>
      <label class="model-select-label" for="model-choice">模型</label>
      <div class="model-select-row"><select id="model-choice" v-model="model" :disabled="busy"><option value="">请选择模型</option><option v-if="model && !models.some(m => m.id === model)" :value="model">{{ model }}</option><option v-for="item in models" :key="item.id" :value="item.id">{{ item.id }}</option></select><button type="button" :disabled="busy || !config.key_configured || editing" @click="loadModels">↻ 获取模型</button></div>
      <div class="model-feedback"><span v-if="error" class="agent-error" role="alert">{{ error }}</span><span v-else role="status">{{ message || (editing ? '保存密钥后可获取模型' : '') }}</span></div>
      <footer class="model-actions"><button type="button" class="model-reload" :disabled="busy" @click="load()">↶ 重新加载</button><div><button type="button" :disabled="busy" @click="emit('close')">取消</button><button class="model-save" :disabled="busy || (editing && !key.trim())">{{ busy ? '处理中…' : '保存' }}</button></div></footer>
    </form>
    <template v-else><p v-if="error" class="agent-error" role="alert">{{ error }}</p><button type="button" :disabled="busy" @click="load(false)">{{ busy ? '加载中…' : '重新连接' }}</button></template>
  </section>
</template>

<style scoped>.config-tabs{display:flex;gap:8px;margin-bottom:18px}.config-tabs button{padding:7px 12px;border-radius:8px}.config-tabs [aria-pressed=true]{background:#e5f0f2;color:#346e7b}</style>
