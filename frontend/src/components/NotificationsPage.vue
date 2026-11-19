<script setup>
import { notificationTitle, readableContent } from '../utils/workflow'
import { patch } from '../api/operationsApi'
import { usePagedList } from '../composables/usePagedList'
import ListPager from './ListPager.vue'
import { dateText } from '../utils/bookingFormat'
import { getPortal } from '../utils/portal'
const { items, total, page, loading, error, load, turn } = usePagedList('/notifications')
async function read(n) {
  try {
    await patch('/notifications/' + n.id + '/read')
    await load()
  } catch (e) {
    error.value = e.userMessage
  }
}
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">STAY IN TOUCH</span>
      <h1>消息中心</h1>
      <p>每一次安排与进展，都在这里。</p>
    </div>
  </div>
  <section class="panel">
    <p class="error-message" role="alert" v-if="error">{{ error }}</p>
    <p v-if="loading">正在加载…</p>
    <p v-else-if="!items.length" class="empty-state">暂时没有新的消息。</p>
    <article class="message-row" v-for="n in items" :key="n.id">
      <div class="message-content">
        <span v-if="!n.readAt" class="unread-dot"></span
        ><strong>{{ notificationTitle(n.title) }}</strong>
        <p>{{ readableContent(n.content) }}</p>
        <small>{{ dateText(n.createdAt) }}</small>
      </div>
      <div class="message-actions">
        <RouterLink
          v-if="n.appointmentId"
          class="text-button"
          :to="'/' + getPortal() + '/appointments/' + n.appointmentId"
          >查看预约</RouterLink
        ><button v-if="!n.readAt" class="outline-button" @click="read(n)">标为已读</button
        ><small v-else>已读</small>
      </div>
    </article>
    <ListPager :page="page" :total="total" :loading="loading" @turn="turn" />
  </section>
</template>
