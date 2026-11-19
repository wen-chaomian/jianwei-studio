import { ref, onMounted } from 'vue'
import { useChangeSync } from './useChangeSync'
import { get } from '../api/operationsApi'
export function usePagedList(url, params = () => ({})) {
  const items = ref([]),
    total = ref(0),
    page = ref(1),
    loading = ref(false),
    error = ref('')
  let sequence = 0,
    activeParams = {}
  async function load(reset = false) {
    if (reset) page.value = 1
    activeParams = { ...params() }
    const seq = ++sequence
    loading.value = true
    error.value = ''
    try {
      const data = await get(url, { page: page.value, size: 10, ...activeParams })
      if (seq === sequence) {
        items.value = data.items
        total.value = data.total
      }
    } catch (e) {
      if (seq === sequence) error.value = e.userMessage
    } finally {
      if (seq === sequence) loading.value = false
    }
  }
  async function turn(delta) {
    page.value += delta
    await load()
  }
  useChangeSync({
    fetchData: async () => {
      const data = await get(url, { page: page.value, size: 10, ...activeParams })
      return { items: data.items, total: data.total }
    },
    current: () => ({ items: items.value, total: total.value }),
    apply: (data) => {
      items.value = data.items
      total.value = data.total
      error.value = ''
    },
    key: () => [sequence, page.value, activeParams],
    paused: () => loading.value,
  })
  onMounted(() => load())
  return { items, total, page, loading, error, load, turn }
}
