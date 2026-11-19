<script setup>
import { ref, nextTick } from 'vue'
import { ElDialog } from 'element-plus'
import 'element-plus/es/components/dialog/style/css'
import ConversationList from '../../components/agent/ConversationList.vue'
import { useAgentChat } from '../../composables/useAgentChat'
import ChatMessages from '../../components/agent/ChatMessages.vue'
import ModelSettings from '../../components/agent/ModelSettings.vue'
import '../../assets/styles/agent.css'
const {
  threads,
  messages,
  selected,
  draft,
  error,
  busy,
  status,
  hasMore,
  older,
  active,
  connection,
  loading,
  isAdmin,
  generating,
  select,
  create,
  send: sendMessage,
  loadOlder,
  checkStatus,
  loadList,
  reconnect, cancelGeneration, stopping, renameThread, removeThread,
} = useAgentChat()
const settingsOpen = ref(false)
const input = ref(null)
function focusInput() { if (!settingsOpen.value) input.value?.focus({ preventScroll: true }) }
async function send() {
  if (busy.value || generating.value) return
  focusInput()
  await sendMessage()
  await nextTick()
  if (document.activeElement === input.value || document.activeElement === document.body || document.activeElement?.closest('.agent-send')) focusInput()
}

</script>
<template>
  <section class="agent-page">
    <div class="agent-workspace">
      <div class="agent-sidebar">
        <div class="agent-sidebar-brand"><small>JIANWEI · AI</small><h1>见微助手</h1></div>
      <ConversationList :threads="threads" :selected="selected" :busy="busy" :ready="!!status" :has-more="hasMore" :rename="renameThread" :remove="removeThread" @create="create" @select="select" @more="loadList(true)" />
        <div v-if="isAdmin" class="agent-sidebar-footer">
          <button class="agent-settings-trigger" @click="settingsOpen = true"><span aria-hidden="true">⚙</span> 模型配置</button>
        </div>
      </div>
      <div class="agent-chat">
    <p v-if="!status?.model_ready" class="agent-notice">
      {{ status?.message || '助手服务暂不可用，请确认服务已启动。' }}
      <button class="text-button" @click="checkStatus">检查连接</button>
    </p>
    <p v-if="error" class="agent-error" role="alert">{{ error }}</p>

        <ChatMessages
          :messages="messages"
          :conversation-id="selected"
          :active="active"
          :loading="loading"
          :older="older"
          @focus-input="focusInput"
          @older="loadOlder"
        />
        <p v-if="connection === 'reconnecting'" class="muted">连接中断，正在恢复回复进度…</p>
        <p v-if="connection === 'disconnected'" class="agent-notice">
          连接已中断，任务可能仍在执行。<button @click="reconnect">恢复连接</button>
        </p>
        <form @submit.prevent="send" class="agent-composer">
          <label class="sr-only" for="agent-input">输入消息</label
          ><textarea
            id="agent-input"
            ref="input"
            v-model="draft"
            rows="2"
            maxlength="4000"
            :disabled="!selected || loading || !status?.model_ready"
            :placeholder="!selected ? '先新建或选择一个对话' : '向见微助手发送消息…'"
            @keydown.enter.exact.prevent="send"
          />
          <div>
            <small class="muted"><span v-if="draft.length > 3500">{{ draft.length }}/4000</span></small>
            <button type="button" class="primary-button agent-send" :class="{ waiting: busy || generating }"
              :aria-label="busy || generating ? '停止生成' : '发送消息'"
              :title="busy || generating ? '停止生成' : '发送消息'"
              :disabled="stopping || (!(busy || generating) && (!selected || !draft.trim() || loading || !status?.model_ready))"
              @click="busy || generating ? cancelGeneration() : send()">
              <template v-if="busy || generating"><span class="agent-spinner" aria-hidden="true"></span><span class="agent-stop-square" aria-hidden="true"></span></template>
              <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 19V5M6 11l6-6 6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
            </button>
          </div>
        </form>
      </div>
    </div>
    <ElDialog v-if="isAdmin" v-model="settingsOpen" title="模型配置" width="min(500px, calc(100vw - 32px))" class="agent-settings-dialog" align-center destroy-on-close :close-on-click-modal="false">
      <ModelSettings @saved="checkStatus" @close="settingsOpen = false" />
    </ElDialog>
  </section>
</template>
