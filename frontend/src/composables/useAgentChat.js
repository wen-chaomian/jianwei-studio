import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from './useAuth'
import { useChangeSync } from './useChangeSync'
import { sessionHeaders, getSessionScope } from '../utils/portalSession'
import { watchAgentRun } from '../utils/agentStream'
import * as api from '../api/agentApi'
import { createChatDrafts } from '../utils/chatDrafts' 
export function useAgentChat() {
  const route = useRoute(),
    router = useRouter()
  const portal = computed(() => route.meta.area)
  const threads = ref([]),
    messages = ref([]),
    selected = ref(null),
    draft = ref(''),
    error = ref(''),
    busy = ref(false)
  const status = ref(null),
    hasMore = ref(false),
    older = ref(false),
    active = ref(null),
    connection = ref(''),
    loading = ref(false)
  let stopRequested = false
  const stopping = ref(false)
  let stream,
    epoch = 0,
    selectVersion = 0,
    pending,
    createKey = crypto.randomUUID()
  const identity = () => `${portal.value}:${auth.user?.id}:${getSessionScope(portal.value)}`
  let currentIdentity = identity()
  const drafts = createChatDrafts(window.sessionStorage)
  let draftOwner = null
  const draftAccount = () => `${portal.value}:${auth.user?.id}`
  watch(draft, value => { if (draftOwner) drafts.save(draftOwner.account, draftOwner.id, value) }, { flush: 'sync' })
  const isAdmin = computed(() => portal.value === 'admin')
  const generating = computed(() => active.value?.status === 'running')
  function stop() {
    stream?.stop()
    stream = null
  }
  function clear() {
    epoch++
    selectVersion++
    stop()
    threads.value = []
    messages.value = []
    draftOwner = null
    selected.value = null
    active.value = null
    draft.value = ''
    error.value = ''
    pending = null
    status.value = null
    busy.value = false
    stopping.value = false
    stopRequested = false
    loading.value = false
    connection.value = ''
    older.value = false
    hasMore.value = false
  }
  async function checkStatus() {
    const e = epoch
    try {
      const result = await api.agentStatus(portal.value)
      if (e === epoch) status.value = result
    } catch (ex) {
      if (e === epoch) {
        status.value = null
        error.value = ex.userMessage || '助手暂不可用'
      }
    }
  }
  async function loadList(more = false) {
    const e = epoch
    const data = await api.conversations(portal.value, more ? threads.value.length : 0)
    if (e !== epoch) return
    threads.value = more ? [...threads.value, ...data.items] : data.items
    hasMore.value = data.has_more
  }
  async function select(id) {
    stop()
    const v = ++selectVersion,
      e = epoch
    selected.value = id
    draftOwner = null
    draft.value = drafts.read(draftAccount(), id)
    draftOwner = { account: draftAccount(), id }
    drafts.select(draftAccount(), id)
    active.value = null
    messages.value = []
    error.value = ''
    loading.value = true
    pending = null
    try {
      const data = await api.conversation(portal.value, id)
      if (e !== epoch || v !== selectVersion) return
      messages.value = data.messages
      older.value = data.has_more
      router.replace({ query: { ...route.query, chat: id } })
      if (data.active_run) attach(data.active_run)
    } catch (ex) {
      if (e === epoch && v === selectVersion) error.value = ex.userMessage || '读取对话失败'
    } finally {
      if (e === epoch && v === selectVersion) loading.value = false
    }
  }
  function attach(runId) {
    stop()
    const e = epoch,
      v = selectVersion,
      account = identity()
    active.value = { id: runId, status: 'running', output: '' }
    stream = watchAgentRun({
      runId,
      headers: sessionHeaders(portal.value),
      onState(value) {
        if (e !== epoch || v !== selectVersion || identity() !== account) return
        connection.value = value
        if (value === 'unauthorized') {
          clear()
          error.value = '登录状态已变化，请重新进入助手'
        }
      },
      onSnapshot(value) {
        if (e !== epoch || v !== selectVersion || identity() !== account) return
        active.value = value
        if (value.status !== 'running') void finish(e, v)
      },
    })
  }
  async function finish(e, v) {
    try {
      const data = await api.conversation(portal.value, selected.value)
      if (e !== epoch || v !== selectVersion) return
      messages.value = data.messages
      older.value = data.has_more
      active.value = null
      await loadList()
    } catch (ex) {
      if (e === epoch) error.value = ex.userMessage || '历史记录加载失败'
    }
  }
  async function create() {
    if (busy.value) return
    busy.value = true
    const e = epoch
    try {
      const row = await api.newConversation(portal.value, createKey)
      if (e !== epoch) return
      createKey = crypto.randomUUID()
      await loadList()
      await select(row.id)
    } catch (ex) {
      if (e === epoch) error.value = ex.userMessage || '新建对话失败'
    } finally {
      if (e === epoch) busy.value = false
    }
  }
  async function send() {
    const content = draft.value.trim()
    if (!content || busy.value || generating.value || !selected.value) return
    busy.value = true
    stopRequested = false
    error.value = ''
    const e = epoch,
      v = selectVersion,
      id = selected.value
    if (!pending || pending.content !== content || pending.id !== id)
      pending = { id, content, request_id: crypto.randomUUID() }
    try {
      const run = await api.sendMessage(portal.value, id, {
        content,
        request_id: pending.request_id,
      })
      if (e !== epoch || v !== selectVersion) return
      pending = null
      if (draft.value.trim() === content) draft.value = ''
      const data = await api.conversation(portal.value, id)
      if (e !== epoch || v !== selectVersion) return
      messages.value = data.messages
      older.value = data.has_more
      attach(run.id)
      if (stopRequested) await cancelGeneration()
    } catch (ex) {
      if (e === epoch && v === selectVersion)
        error.value = (ex.userMessage || '发送失败') + '；可再次发送，相同内容不会重复创建任务。'
    } finally {
      if (e === epoch) busy.value = false
    }
  }
  async function cancelGeneration() {
    stopRequested = true
    if (!active.value?.id || stopping.value) return
    const e = epoch, v = selectVersion, id = active.value.id
    stopping.value = true
    try {
      await api.stopRun(portal.value, id)
      if (e !== epoch || v !== selectVersion) return
      stop()
      await finish(e, v)
    } catch (ex) {
      if (e === epoch && v === selectVersion) error.value = ex.userMessage || '停止失败，请重试'
    } finally { if (e === epoch) stopping.value = false }
  }
  async function renameThread(id, title) {
    const e = epoch
    await api.renameConversation(portal.value, id, title)
    if (e === epoch) await loadList()
  }
  async function removeThread(id) {
    const e = epoch
    await api.deleteConversation(portal.value, id)
    if (e !== epoch) return
    drafts.remove(draftAccount(), id)
    if (selected.value === id) {
      draftOwner = null
      stop(); selectVersion++; selected.value = null; messages.value = []; active.value = null
      connection.value = ''; draft.value = ''; older.value = false
      await router.replace({ query: {} })
    }
    await loadList()
    if (!selected.value && threads.value.length) await select(threads.value[0].id)
  }
  async function loadOlder() {
    if (!older.value || !messages.value.length) return
    const e = epoch,
      v = selectVersion
    try {
      const data = await api.conversation(portal.value, selected.value, messages.value[0].seq)
      if (e !== epoch || v !== selectVersion) return
      messages.value = [...data.messages, ...messages.value]
      older.value = data.has_more
    } catch (ex) {
      if (e === epoch) error.value = ex.userMessage
    }
  }
  async function init() {
    const e = epoch
    await checkStatus()
    try {
      await loadList()
      if (e !== epoch) return
      const remembered = drafts.last(draftAccount())
      const id = route.query.chat || remembered || threads.value[0]?.id
      if (id) await select(id)
    } catch (ex) {
      if (e === epoch) error.value = ex.userMessage || '助手暂不可用'
    }
  }
  function sessionChanged() {
    if (identity() !== currentIdentity) {
      clear()
      currentIdentity = identity()
      if (auth.user) void init()
    }
  }
  watch(() => [portal.value, auth.user?.id], sessionChanged)
  useChangeSync({
    fetchData: () => api.agentStatus(portal.value),
    current: () => status.value,
    apply: (value) => {
      status.value = value
    },
    key: identity,
    paused: () => !auth.user,
  })
  onMounted(() => {
    window.addEventListener('studio-session-change', sessionChanged)
    window.addEventListener('focus', checkStatus)
    void init()
  })
  onBeforeUnmount(() => {
    clear()
    window.removeEventListener('studio-session-change', sessionChanged)
    window.removeEventListener('focus', checkStatus)
  })
  return {
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
    send,
    loadOlder,
    checkStatus,
    loadList,
    cancelGeneration, stopping, renameThread, removeThread,
    reconnect: () => active.value && attach(active.value.id),
  }
}
