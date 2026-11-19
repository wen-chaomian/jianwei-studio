<script setup>
import { auth } from '../composables/useAuth'

import { getPortal } from '../utils/portal'

import { today, dateText, money } from '../utils/bookingFormat'

import { useBookingForm } from '../composables/useBookingForm.js'

const props = defineProps({
  packageId: { type: Number, default: null },
  staff: { type: Boolean, default: false },
})
const emit = defineEmits(['created'])
const {
  rules,
  packages,
  packageId,
  photographerId,
  date,
  slots,
  selected,
  remark,
  customerName,
  customerPhone,
  confirming,
  busy,
  loading,
  error,
  result,
  selectedPackage,
  loadSlots,
  confirm,
  submit,
} = useBookingForm(props, emit)
</script>
<template>
  <section class="panel">
    <template v-if="result"
      ><span class="eyebrow">BOOKING CONFIRMED</span>
      <h2>预约成功，期待与你见面。</h2>
      <p>{{ result.packageName }} · {{ dateText(result.startTime) }}</p>
      <p>{{ result.photographerName }} · {{ result.venueName }}</p>
      <p>应收 ¥{{ money(result.amount) }}，到店付款。</p>
      <RouterLink class="primary-button" :to="'/' + getPortal() + '/appointments/' + result.id"
        >查看预约详情 →</RouterLink
      ></template
    ><template v-else
      ><h2>{{ staff ? '代客创建预约' : '选择你的拍摄时间' }}</h2>
      <p role="alert" class="error-message" v-if="error">{{ error }}</p>
      <form @submit.prevent="confirm">
        <fieldset :disabled="busy" class="booking-fields">
          <div class="booking-form">
            <label
              >拍摄套餐<select v-model="packageId" required :disabled="!!props.packageId">
                <option value="" disabled>请选择套餐</option>
                <option v-for="p in packages" :value="p.id">
                  {{ p.name }} · ¥{{ money(p.price) }}
                </option>
              </select></label
            ><label
              >摄影师<select v-model="photographerId" required>
                <option value="" disabled>请选择摄影师</option>
                <option v-for="p in selectedPackage?.photographers || []" :value="p.id">
                  {{ p.name }}
                </option>
              </select></label
            ><label>拍摄日期<input type="date" v-model="date" :min="today()" required /></label
            ><template v-if="staff"
              ><label>客户姓名<input v-model="customerName" maxlength="40" required /></label
              ><label
                >客户手机号<input
                  v-model="customerPhone"
                  pattern="1[3-9][0-9]{9}"
                  maxlength="11"
                  required /></label></template
            ><label class="full"
              >备注<textarea
                v-model="remark"
                maxlength="500"
                rows="2"
                placeholder="可填写拍摄偏好或需要提前说明的事项"
              ></textarea>
            </label>
          </div>
          <p class="subtle" v-if="selectedPackage">
            {{ selectedPackage.durationMinutes }}分钟拍摄，另含{{
              selectedPackage.bufferMinutes
            }}分钟缓冲。场地自动匹配。至少提前{{ rules?.minAdvanceHours }}小时，可预约未来{{
              rules?.maxAdvanceDays
            }}天。
          </p>
          <p v-if="loading">正在查询空档…</p>
          <div class="slot-grid">
            <button
              type="button"
              v-for="slot in slots"
              :key="slot.startTime"
              :class="{ active: selected === slot.startTime }"
              @click="selected = slot.startTime"
            >
              {{ slot.startTime.slice(11, 16) }}
            </button>
          </div>
          <p v-if="!loading && !slots.length" class="empty-state">
            当天暂无可预约时段，请更换日期或摄影师。
          </p>
          <button class="primary-button" :disabled="!selected || loading || busy">
            {{ auth.user ? '核对预约信息' : '登录后预约' }} →
          </button>
        </fieldset>
      </form>
      <div class="booking-confirm" v-if="confirming">
        <h3>请确认这次预约</h3>
        <p>{{ selectedPackage?.name }} · {{ dateText(selected) }}</p>
        <p>
          {{ selectedPackage?.photographers.find((p) => p.id === Number(photographerId))?.name }} ·
          应收 ¥{{ money(selectedPackage?.price) }}，到店付款
        </p>
        <small>提交时会再次校验空档；若已被占用，请刷新空档后重新选择。</small>
        <div class="form-actions">
          <button class="primary-button" :disabled="busy" @click="submit">
            {{ busy ? '提交中…' : '确认预约' }}</button
          ><button class="outline-button" :disabled="busy" @click="confirming = false">
            返回修改</button
          ><button class="text-button" :disabled="busy" @click="loadSlots">刷新空档</button>
        </div>
      </div></template
    >
  </section>
</template>
