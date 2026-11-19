<script setup>
import { useChangeSync } from '../../composables/useChangeSync'

import { ref, onMounted } from 'vue'
import { get } from '../../api/operationsApi'
import { today, money } from '../../utils/bookingFormat'
const to = ref(today()),
  from = ref(today().slice(0, 8) + '01'),
  data = ref(null),
  error = ref(''),
  busy = ref(false)
async function load() {
  busy.value = true
  error.value = ''
  try {
    data.value = await get('/admin/dashboard', { from: from.value, to: to.value })
  } catch (e) {
    error.value = e.userMessage
  } finally {
    busy.value = false
  }
}
onMounted(load)
useChangeSync({
  fetchData: () => get('/admin/dashboard', { from: from.value, to: to.value }),
  current: () => data.value,
  apply: (value) => {
    data.value = value
    error.value = ''
  },
  key: () => [from.value, to.value],
  paused: () => busy.value,
})
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">STUDIO OVERVIEW</span>
      <h1>每一份信任，都值得记录。</h1>
      <p>经营概览 · 见微工作室</p>
    </div>
    <RouterLink class="primary-button" to="/admin/appointments">查看预约 →</RouterLink>
  </div>
  <section class="panel">
    <form class="list-toolbar" @submit.prevent="load">
      <label>开始日期<input type="date" v-model="from" required /></label
      ><label>结束日期<input type="date" v-model="to" required /></label
      ><button class="outline-button" :disabled="busy">更新统计</button>
    </form>
    <p role="alert" class="error-message" v-if="error">{{ error }}</p>
    <div v-if="data" class="metric-grid">
      <article>
        <small>预约数量</small><strong>{{ data.bookings.appointments || 0 }}</strong>
      </article>
      <article>
        <small>完成拍摄</small><strong>{{ data.bookings.completed || 0 }}</strong>
      </article>
      <article>
        <small>已取消 / 已爽约</small
        ><strong>{{ data.bookings.cancelled || 0 }} / {{ data.bookings.noShow || 0 }}</strong>
      </article>
      <article>
        <small>待处理申请</small><strong>{{ data.pendingRequests }}</strong>
      </article>
      <article>
        <small>登记收款</small><strong>¥{{ money(data.revenue.received) }}</strong>
      </article>
      <article>
        <small>登记退款</small><strong>¥{{ money(data.revenue.refunded) }}</strong>
      </article>
      <article>
        <small>净收款</small><strong>¥{{ money(data.revenue.net) }}</strong>
      </article>
    </div>
    <p class="subtle">
      预约按拍摄日期统计，收退款按流水登记日期统计；待处理申请为当前待处理及处理中的总数。
    </p>
  </section>
  <div class="quick-grid">
    <RouterLink to="/admin/schedules"
      ><h3>安排接下来的拍摄</h3>
      <p>摄影师与场地排班 ↗</p></RouterLink
    ><RouterLink to="/admin/requests"
      ><h3>回应客户的需要</h3>
      <p>处理改期、取消与其他申请 ↗</p></RouterLink
    ><RouterLink to="/admin/customers"
      ><h3>了解每一位客户</h3>
      <p>客户资料与历史记录 ↗</p></RouterLink
    >
  </div>
</template>
