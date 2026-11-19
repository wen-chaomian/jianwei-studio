<script setup>
import { ref, computed, onBeforeUnmount } from 'vue'
import { confirmationRows } from '../../utils/confirmationRows'
import { ElDialog, ElMessage } from 'element-plus'
import { get, post } from '../../api/operationsApi'
const props = defineProps({ id: String, label: String })
const emit = defineEmits(['closed'])
const visible = ref(false), card = ref(null), error = ref(''), busy = ref(false)
const labels = { DONE:'已完成', CANCELLED:'已撤销', REPLACED:'已有新的确认单', EXPIRED:'已过期' }
const rows = computed(() => confirmationRows(card.value?.summary || ''))
const path = () => '/assistant/actions/' + props.id
let requestVersion = 0
onBeforeUnmount(() => { requestVersion++ })
async function open(pendingOnly = false) {
  const version = ++requestVersion
  visible.value = !pendingOnly; busy.value = true; error.value = ''; card.value = null
  try {
    const result = await get(path())
    if (version !== requestVersion) return
    card.value = result
    visible.value = !pendingOnly || result.state === 'PENDING'
  } catch(e) {
    if (version !== requestVersion) return
    error.value = e.userMessage; visible.value = true
  } finally { if (version === requestVersion) busy.value = false }
}
defineExpose({ open })
async function decide(decision) {
  if(busy.value) return
  const version = requestVersion
  busy.value = true; error.value = ''
  try {
    const result = await post(path() + '/' + decision)
    if (version !== requestVersion) return
    card.value = result
    if (decision === 'confirm' && result.state === 'DONE') {
      ElMessage.success(result.receipt || `${result.title || '操作'}已完成`)
      visible.value = false
    }
  } catch(e) {
    if (version !== requestVersion) return
    error.value = e.userMessage
    try {
      const result = await get(path())
      if (version === requestVersion) card.value = result
    } catch { if (version === requestVersion) card.value = null }
  } finally { if (version === requestVersion) busy.value = false }
}

</script>
<template>
  <ElDialog v-model="visible" :title="card?.title || '核对办理内容'" width="min(480px, 92vw)" class="agent-action-dialog" align-center @closed="emit('closed')">
    <p v-if="busy && !card" role="status" class="muted">正在读取确认内容…</p>
    <p v-if="error" role="alert" class="error-message">{{ error }}</p>
    <template v-if="card">
      <dl class="confirmation-fields"><template v-for="(row, index) in rows" :key="index"><dt v-if="row.label">{{ row.label }}</dt><dd :class="{ 'full-row': !row.label }">{{ row.value }}</dd></template></dl>
      <p v-if="card.state !== 'PENDING'" role="status">{{ card.receipt || labels[card.state] }}</p>
    </template>
    <template #footer>
      <template v-if="card?.state === 'PENDING'">
        <button class="secondary-button" :disabled="busy" @click="decide('cancel')">取消本次操作</button>
        <button class="primary-button" :disabled="busy" @click="decide('confirm')">确认{{ card.title }}</button>
      </template>
      <button v-else class="secondary-button" @click="visible=false">关闭</button>
    </template>
  </ElDialog>
</template>
<style scoped>
.confirmation-fields{display:grid;grid-template-columns:84px minmax(0,1fr);gap:12px 18px;margin:4px 0;max-height:55vh;overflow:auto;font-size:15px;line-height:1.7}
.confirmation-fields dt{color:var(--sub)}
.confirmation-fields dd{margin:0;white-space:pre-wrap;overflow-wrap:anywhere}
.confirmation-fields .full-row{grid-column:1/-1}
</style>
