<script setup>
import CustomerPreferences from './CustomerPreferences.vue'
import { ref, onMounted } from 'vue'
import { get, patch } from '../api/operationsApi'
import { loadUser, auth } from '../composables/useAuth'
const form = ref(null),
  error = ref(''),
  notice = ref(''),
  busy = ref(false)
onMounted(async () => {
  try {
    form.value = await get('/profile')
  } catch (e) {
    error.value = e.userMessage
  }
})
async function save() {
  busy.value = true
  try {
    await patch('/profile', form.value)
    await loadUser()
    notice.value = '个人资料已更新。'
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
      <span class="eyebrow">YOUR PROFILE</span>
      <h1>个人中心</h1>
      <p>很高兴在见微遇见你。</p>
    </div>
  </div>
  <section class="panel">
    <p role="alert" class="error-message" v-if="error">{{ error }}</p>
    <p class="success-message" v-if="notice">{{ notice }}</p>
    <form v-if="form" @submit.prevent="save">
      <div class="booking-form">
        <label>姓名 / 称呼<input v-model="form.displayName" maxlength="40" required /></label
        ><label>登录手机号<input :value="form.phone" disabled /></label
        ><label v-if="auth.user?.roles.includes('CUSTOMER')"
          >联系电话<input v-model="form.contactPhone" pattern="1[3-9][0-9]{9}" required
        /></label>
      </div>
      <p class="subtle">联系方式变更不影响登录账号和历史预约。忘记密码请联系工作室核实处理。</p>
      <button class="primary-button" :disabled="busy">保存资料</button>
    </form>
  </section>
  <CustomerPreferences v-if="auth.user?.roles.includes('CUSTOMER')" />
</template>
