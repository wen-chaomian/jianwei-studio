<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import MessageText from './MessageText.vue'
import ResultLinks from './ResultLinks.vue'
import ActionCard from './ActionCard.vue'
const props = defineProps({ messages: Array, active: Object, loading: Boolean, older: Boolean, conversationId: String })
const emit = defineEmits(['older', 'focus-input'])
const panel = ref(null), current = ref(null), away = ref(false)
const actionDialog = ref(null), selectedAction = ref(null)
const announced = new Set()
let actionEpoch = 0
async function showAction(action, pendingOnly = false) {
  const e = ++actionEpoch
  selectedAction.value = action
  await nextTick()
  if (e === actionEpoch) await actionDialog.value?.open(pendingOnly)
}
watch(() => props.conversationId, () => {
  actionEpoch++; selectedAction.value = null; announced.clear()
}, { flush: 'sync' })
// Only a live run completion can open a dialog. Loading saved messages never does.
watch(() => props.active, run => {
  if (run?.status !== 'completed') return
  const item = (run.result_links || []).find(x => /^\/(customer|staff|admin)\/assistant\/actions\/[0-9a-f-]{36}$/.test(x.url || ''))
  if (!item || announced.has(item.url)) return
  announced.add(item.url)
  void showAction({ id: item.url.split('/').pop(), label: item.label }, true)
}, { flush: 'sync' })
const marks = computed(() => (props.messages || []).filter(m => m.role === 'user'))
let forceBottom = true, priorHeight = 0
function updatePosition() {
  const el = panel.value
  if (!el) return
  away.value = el.scrollHeight - el.scrollTop - el.clientHeight > 100
  const top = el.getBoundingClientRect().top
  const nodes = [...el.querySelectorAll('[data-user-message]')]
  const passed = nodes.filter(n => n.getBoundingClientRect().top <= top + el.clientHeight * .45)
  current.value = (passed.at(-1) || nodes[0])?.dataset.userMessage
}
function jump(seq) {
  const el = panel.value, node = el?.querySelector(`[data-user-message="${seq}"]`)
  if (node) el.scrollTo({ top: el.scrollTop + node.getBoundingClientRect().top - el.getBoundingClientRect().top - 20, behavior: 'smooth' })
}
function bottom() { if (panel.value) panel.value.scrollTo({ top: panel.value.scrollHeight, behavior: 'smooth' }) }
watch(() => props.conversationId, () => { forceBottom = true; current.value = null; away.value = false })
watch(() => [props.messages?.length, props.active?.output, props.loading], async () => {
  const el = panel.value, near = !away.value
  await nextTick()
  if (!panel.value) return
  if (priorHeight) { panel.value.scrollTop += panel.value.scrollHeight - priorHeight; priorHeight = 0 }
  else if (near || forceBottom) panel.value.scrollTop = panel.value.scrollHeight
  if (props.messages?.length) forceBottom = false
  updatePosition()
})
function older() { priorHeight = panel.value?.scrollHeight || 0; emit('older') }
</script>
<template>
  <div class="agent-message-area">
    <nav v-if="marks.length" class="agent-ruler" aria-label="消息定位">
      <button v-for="(message, index) in marks" :key="message.seq" :class="{ current: current === String(message.seq) }" :title="message.content" :aria-label="`定位第${index + 1}条消息：${message.content.slice(0, 50)}`" @click="jump(message.seq)"><span></span></button>
    </nav>
    <div class="agent-messages" ref="panel" role="log" aria-label="对话消息" aria-live="polite" @scroll="updatePosition">
      <button v-if="props.older" class="text-button" @click="older">查看更早消息</button>
      <p v-if="loading" class="muted">正在加载对话…</p>
      <div v-else-if="!messages.length && !active" class="agent-empty"><span class="agent-empty-mark">✳</span><h2>今天，想聊点什么？</h2><p>拍摄前的准备、灵感，或一个简单的问题。</p><small>你的对话仅当前账号可见</small></div>
      <article v-for="item in messages" :key="item.seq" :data-user-message="item.role === 'user' ? item.seq : undefined" :class="['agent-message', item.role]">
        <span v-if="item.role !== 'user'" class="agent-author">✳ 见微助手</span>
        <MessageText :content="item.content" />
        <ResultLinks v-if="item.role === 'assistant' && item.status === 'completed'" :links="item.result_links" @action="showAction" />
        <p v-if="item.role === 'assistant' && item.status === 'failed'" :class="item.error_message === '已停止生成' ? 'muted' : 'agent-error'">{{ item.error_message }}<span v-if="item.content && item.error_message !== '已停止生成'">（上方为未完成的回复）</span></p>
      </article>
      <article v-if="active && !messages.some(m => m.run_id === active.id && m.role === 'assistant')" class="agent-message assistant">
        <span class="agent-author">✳ 见微助手</span><MessageText :content="active.output || active.progress_label || '正在思考…'" />
        <small v-if="active.status === 'running'" class="muted">{{ active.progress_label || '回复中' }}</small>
        <ResultLinks v-if="active.status === 'completed'" :links="active.result_links" @action="showAction" />
        <p v-if="active.error_message" class="agent-error">{{ active.error_message }}</p>
      </article>
    </div>
    <ActionCard v-if="selectedAction" :key="selectedAction.id" ref="actionDialog" :id="selectedAction.id" :label="selectedAction.label" @closed="emit('focus-input')" />
    <button v-if="away" class="agent-latest" @click="bottom" aria-label="回到最新消息">↓ 回到最新</button>
  </div>
</template>
