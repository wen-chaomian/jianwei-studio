<script setup>
import { ref, computed } from 'vue'
import { get, patch, post } from '../api/operationsApi'
import { usePagedList } from '../composables/usePagedList'
import ListPager from './ListPager.vue'
import { dateText, money, statusText } from '../utils/bookingFormat'
import { getPortal } from '../utils/portal'
import { auth } from '../composables/useAuth'
const q = ref(''),
  detail = ref(null),
  form = ref(null),
  password = ref(''),
  busy = ref(false),
  notice = ref(''),
  historyPage = ref(1)
const { items, total, page, loading, error, load, turn } = usePagedList('/customers', () => ({
  q: q.value,
}))
const admin = computed(() => auth.user?.roles.includes('ADMIN'))
async function open(id, p = 1) {
  try {
    detail.value = await get('/customers/' + id, { page: p, size: 10 })
    form.value = { ...detail.value.profile, remark: detail.value.profile.remark || '' }
    historyPage.value = p
    password.value = ''
    notice.value = ''
  } catch (e) {
    error.value = e.userMessage
  }
}
async function save() {
  busy.value = true
  try {
    await patch('/customers/' + form.value.id, form.value)
    notice.value = '客户资料已保存'
    await load()
  } catch (e) {
    error.value = e.userMessage
  } finally {
    busy.value = false
  }
}
async function reset() {
  busy.value = true
  try {
    await post('/admin/customers/' + form.value.id + '/reset-password', {
      password: password.value,
    })
    password.value = ''
    notice.value = '密码已重置，原登录会话已失效。'
  } catch (e) {
    error.value = e.userMessage
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">PEOPLE & STORIES</span>
      <h1>客户档案</h1>
      <p>记住每位客户的偏好，也记住每次相遇。</p>
    </div>
  </div>
  <p role="alert" class="error-message" v-if="error">{{ error }}</p>
  <section class="panel" v-if="detail">
    <div class="section-title">
      <h2>{{ detail.profile.name }}的档案</h2>
      <button class="text-button" @click="detail = null">收起</button>
    </div>
    <p class="success-message" v-if="notice">{{ notice }}</p>
    <form @submit.prevent="save">
      <div class="booking-form">
        <label>称呼<input v-model="form.name" maxlength="40" required /></label
        ><label>联系电话<input v-model="form.phone" pattern="1[3-9][0-9]{9}" required /></label
        ><label class="full"
          >客户备注<textarea v-model="form.remark" maxlength="500" rows="2"></textarea>
        </label>
      </div>
      <button class="outline-button" :disabled="busy">保存资料</button>
    </form>
    <form v-if="admin && detail.profile.userId" class="list-toolbar" @submit.prevent="reset">
      <label
        >核实身份后重置密码<input
          type="password"
          minlength="8"
          maxlength="72"
          v-model="password"
          required /></label
      ><button class="outline-button" :disabled="busy">确认重置</button>
    </form>
    <h3>历史预约</h3>
    <p v-if="!detail.history.items.length">暂无历史预约。</p>
    <RouterLink
      class="history-row"
      v-for="a in detail.history.items"
      :key="a.id"
      :to="'/' + getPortal() + '/appointments/' + a.id"
      ><span>{{ dateText(a.startTime) }} · {{ a.packageName }}</span
      ><span>{{ statusText[a.status] }} · ¥{{ money(a.amount) }} ↗</span></RouterLink
    ><ListPager
      :page="historyPage"
      :total="detail.history.total"
      @turn="(d) => open(form.id, historyPage + d)"
    />
  </section>
  <section class="panel">
    <form class="list-toolbar" @submit.prevent="load(true)">
      <input v-model="q" maxlength="80" placeholder="搜索姓名或手机号" /><button
        class="primary-button"
      >
        搜索
      </button>
    </form>
    <div class="table-scroll">
      <table class="data-table">
        <thead>
          <tr>
            <th>姓名</th>
            <th>联系电话</th>
            <th>来源</th>
            <th>备注</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in items" :key="c.id">
            <td>{{ c.name }}</td>
            <td>{{ c.phone }}</td>
            <td>{{ c.userId ? '已注册' : '线下客户' }}</td>
            <td>{{ c.remark || '—' }}</td>
            <td><button class="text-button" @click="open(c.id)">查看档案 ↗</button></td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="!loading && !items.length" class="empty-state">没有匹配的客户。</p>
    <ListPager :page="page" :total="total" :loading="loading" @turn="turn" />
  </section>
</template>
