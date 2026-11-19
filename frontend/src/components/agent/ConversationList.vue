<script setup>
import { ref } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
const props = defineProps({ threads: Array, selected: String, busy: Boolean, ready: Boolean, hasMore: Boolean, rename: Function, remove: Function })
const emit = defineEmits(['create', 'select', 'more'])
const menu = ref(null)
async function rename(thread) {
  menu.value = null
  try {
    const { value } = await ElMessageBox.prompt('为这段对话起一个名字', '重命名对话', {
      inputValue: thread.title, confirmButtonText: '保存', cancelButtonText: '取消',
      inputValidator: value => !!value?.trim() && value.trim().length <= 80 || '请输入 1–80 个字符',
    })
    await props.rename(thread.id, value.trim())
  } catch (ex) { if (ex !== 'cancel' && ex !== 'close') ElMessage.error(ex.userMessage || '重命名失败') }
}
async function remove(thread) {
  menu.value = null
  try {
    await ElMessageBox.confirm('删除后，这段对话将从列表中移除。确认删除吗？', '删除对话', { confirmButtonText: '删除', cancelButtonText: '保留', type: 'warning' })
    await props.remove(thread.id)
  } catch (ex) { if (ex !== 'cancel' && ex !== 'close') ElMessage.error(ex.userMessage || '删除失败') }
}
</script>
<template>
  <nav class="agent-threads" aria-label="对话列表" @keydown.esc="menu = null">
    <button class="primary-button" :disabled="busy || !ready" @click="emit('create')">＋ 新对话</button>
    <small class="agent-history-label">最近对话</small>
    <div v-for="thread in threads" :key="thread.id" class="agent-thread-row" :class="{ selected: thread.id === selected }">
      <button class="agent-thread-title" :title="thread.title" :disabled="busy" @click="menu = null; emit('select', thread.id)">{{ thread.title }}</button>
      <button class="agent-thread-menu" :aria-label="'对话操作：' + thread.title" :aria-expanded="menu === thread.id" :disabled="busy" @click="menu = menu === thread.id ? null : thread.id">⋯</button>
      <div v-if="menu === thread.id" class="agent-thread-actions">
        <button @click="rename(thread)">重命名</button><button class="delete" @click="remove(thread)">删除</button>
      </div>
    </div>
    <button v-if="hasMore" @click="emit('more')">更多对话</button>
    <p v-if="!threads.length" class="muted">暂无历史对话</p>
  </nav>
</template>
