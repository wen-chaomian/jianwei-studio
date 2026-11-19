<script setup>
import { computed } from 'vue'
import { usePagedList } from '../composables/usePagedList'
import ListPager from './ListPager.vue'
import { auth } from '../composables/useAuth'
import { dateText } from '../utils/bookingFormat'
import { getPortal } from '../utils/portal'
import { requestLabels as labels } from '../utils/workflow'
const { items, total, page, loading, error, load, turn } = usePagedList('/customer-requests')
const canHandle = computed(() =>
  auth.user?.roles.some((x) => ['ADMIN', 'RECEPTIONIST'].includes(x)),
)
const target = (q) => ({
  path: '/' + getPortal() + '/appointments/' + q.appointmentId,
  query: { request: q.id },
  hash: '#appointment-requests',
})
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">SERVICE REQUESTS</span>
      <h1>{{ canHandle ? '申请处理' : '我的申请' }}</h1>
      <p>
        {{
          canHandle ? '核对客户诉求，在预约中完成办理并同步处理结果。' : '查看工作室的处理进展。'
        }}
      </p>
    </div>
  </div>
  <p role="alert" class="error-message" v-if="error">{{ error }}</p>
  <section class="panel">
    <p v-if="loading">正在加载…</p>
    <p v-else-if="!items.length" class="empty-state">暂无申请记录。</p>
    <article class="message-row request-row" v-for="q in items" :key="q.id">
      <div class="message-content">
        <span class="phase-badge">{{ labels[q.status] }}</span>
        <h3>{{ labels[q.type] }} · {{ q.customerName }}</h3>
        <p>{{ q.content }}</p>
        <p v-if="q.result">处理结果：{{ q.result }}</p>
        <small>{{ dateText(q.createdAt) }}</small>
      </div>
      <div class="message-actions">
        <RouterLink class="text-button" :to="target(q)">查看预约 ↗</RouterLink
        ><RouterLink
          v-if="canHandle && ['PENDING', 'PROCESSING'].includes(q.status)"
          class="outline-button"
          :to="target(q)"
          >处理申请</RouterLink
        >
      </div>
    </article>
    <ListPager :page="page" :total="total" :loading="loading" @turn="turn" />
  </section>
</template>
