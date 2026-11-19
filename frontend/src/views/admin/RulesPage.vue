<script setup>
import { useChangeSync } from '../../composables/useChangeSync'

import { ref, onMounted } from 'vue'
import { get, patch } from '../../api/operationsApi'
const form = ref(null),
  error = ref(''),
  notice = ref(''),
  busy = ref(false),
  baseline = ref('')
onMounted(async () => {
  try {
    form.value = await get('/admin/booking-rule')
    baseline.value = JSON.stringify(form.value)
  } catch (e) {
    error.value = e.userMessage
  }
})
async function save() {
  error.value = ''
  notice.value = ''
  busy.value = true
  try {
    await patch('/admin/booking-rule', form.value)
    baseline.value = JSON.stringify(form.value)
    notice.value = '规则已保存，后续预约与变更按新规则校验。'
  } catch (e) {
    error.value = e.userMessage
  } finally {
    busy.value = false
  }
}
useChangeSync({
  fetchData: () => get('/admin/booking-rule'),
  current: () => form.value,
  apply: (value) => {
    form.value = value
    baseline.value = JSON.stringify(value)
  },
  paused: () => busy.value || !form.value || JSON.stringify(form.value) !== baseline.value,
})
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">STUDIO SETTINGS</span>
      <h1>业务规则</h1>
      <p>让每一次预约都有清晰的约定。</p>
    </div>
  </div>
  <section class="panel">
    <p role="alert" class="error-message" v-if="error">{{ error }}</p>
    <p class="success-message" v-if="notice">{{ notice }}</p>
    <form v-if="form" @submit.prevent="save">
      <div class="booking-form">
        <label>营业开始<input type="time" v-model="form.openTime" required /></label
        ><label>营业结束<input type="time" v-model="form.closeTime" required /></label
        ><label
          >预约开始间隔<select v-model.number="form.slotMinutes">
            <option :value="15">15 分钟</option>
            <option :value="30">30 分钟</option>
            <option :value="60">60 分钟</option>
          </select></label
        ><label
          >最少提前（小时）<input
            type="number"
            min="0"
            max="168"
            v-model.number="form.minAdvanceHours"
            required /></label
        ><label
          >最多提前（天）<input
            type="number"
            min="1"
            max="90"
            v-model.number="form.maxAdvanceDays"
            required /></label
        ><label
          >自助改期取消提前（小时）<input
            type="number"
            min="0"
            max="720"
            v-model.number="form.changeCutoffHours"
            required
        /></label>
      </div>
      <p class="subtle">
        营业时间与资源排班共同决定空档。已有预约保留；规则调整不会自动改期或取消。
      </p>
      <button class="primary-button" :disabled="busy">保存规则</button>
    </form>
  </section>
</template>
