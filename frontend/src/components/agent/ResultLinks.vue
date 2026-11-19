<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
const props = defineProps({ links: Array })
const emit = defineEmits(['action'])
const route = useRoute()
const links = computed(() => (props.links || []).filter(item => {
  const portal = route.path.split('/')[1]
  return typeof item.url === 'string' && new RegExp(`^/${portal}(?:/appointments(?:/[0-9]+)?|/assistant/actions/[0-9a-f-]{36}|/profile|/knowledge|/requests|/schedules|/notices(?:/[0-9]+/[0-9]+)?)?$`).test(item.url)
}))
</script>
<template>
  <nav v-if="links.length" aria-label="查询结果入口" class="agent-result-links">
    <template v-for="item in links" :key="item.url">
      <button v-if="item.url.includes('/assistant/actions/')" type="button" class="text-button agent-result-link"
        @click="emit('action', { id: item.url.split('/').pop(), label: item.label })">{{ item.label }} <span aria-hidden="true">↗</span></button>
      <RouterLink v-else class="agent-result-link" :to="item.url">{{ item.label }} <span aria-hidden="true">↗</span></RouterLink>
    </template>
  </nav>
</template>
<style scoped>
.agent-result-links{display:flex;align-items:center;gap:8px 20px;flex-wrap:wrap;margin-top:12px}
.agent-result-links .agent-result-link{display:inline-flex;align-items:center;gap:4px;min-height:28px;margin:0;padding:0;border:0;border-radius:0;background:transparent;color:var(--primary,#376b76);font:inherit;font-size:13px;line-height:1.5;text-decoration:none;white-space:nowrap;cursor:pointer}
.agent-result-links .agent-result-link:hover{text-decoration:underline;text-underline-offset:3px}
.agent-result-link:focus-visible{outline:2px solid var(--primary);outline-offset:4px}
</style>
