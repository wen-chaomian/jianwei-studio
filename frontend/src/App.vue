<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { watchSessionLogout } from './utils/portalSession'
import { invalidateSession } from './composables/useAuth'
import { portalFor } from './utils/portal'
const router = useRouter()
let stopWatchingLogout
function preventNumberStep(event) {
  const input = event.target
  if (!(input instanceof HTMLInputElement) || input.type !== 'number') return
  if (event.type === 'keydown' && (event.key === 'ArrowUp' || event.key === 'ArrowDown'))
    event.preventDefault()
  if (event.type === 'wheel' && document.activeElement === input) event.preventDefault()
}
onMounted(() => {
  stopWatchingLogout = watchSessionLogout((portal) => {
    invalidateSession(portal)
    const route = router.currentRoute.value
    if (portalFor(route.path) === portal) {
      void router.replace({ path: `/${portal}/login`, query: { error: 'session-ended', redirect: route.fullPath } })
    }
  })
  document.addEventListener('keydown', preventNumberStep, true)
  document.addEventListener('wheel', preventNumberStep, { capture: true, passive: false })
})
onUnmounted(() => {
  stopWatchingLogout?.()
  document.removeEventListener('keydown', preventNumberStep, true)
  document.removeEventListener('wheel', preventNumberStep, true)
})
</script>
<template><RouterView /></template>
