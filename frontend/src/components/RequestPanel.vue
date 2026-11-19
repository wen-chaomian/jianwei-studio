<script setup>
import { dateText, today } from '../utils/bookingFormat'

import { requestLabels as labels } from '../utils/workflow'

import { useRequestHandling } from '../composables/useRequestHandling.js'

const props = defineProps({ item: Object, activity: Object, editable: Boolean })
const emit = defineEmits(['updated', 'editing'])
const {
  selected,
  decision,
  result,
  photographer,
  date,
  slots,
  slot,
  busy,
  loading,
  error,
  needsExplanation,
  changing,
  approveLabel,
  edit,
  loadSlots,
  save,
} = useRequestHandling(props, emit)
</script>
<template>
  <section class="panel" id="appointment-requests">
    <h2>客户申请</h2>
    <article class="message-row request-row" v-for="q in activity.requests" :key="q.id">
      <div class="message-content">
        <span class="phase-badge">{{ labels[q.status] }}</span>
        <h3>{{ labels[q.type] }} #{{ q.id }}</h3>
        <p>{{ q.content }}</p>
        <p v-if="q.result">处理结果：{{ q.result }}</p>
        <small>{{ dateText(q.createdAt) }}</small>
      </div>
      <div class="message-actions" v-if="editable && ['PENDING', 'PROCESSING'].includes(q.status)">
        <button class="outline-button" :disabled="busy" @click="edit(q)">处理申请</button>
      </div>
    </article>
    <form v-if="selected" @submit.prevent="save">
      <h3>处理{{ labels[selected.type] }} #{{ selected.id }}</h3>
      <p role="alert" class="error-message" v-if="error">{{ error }}</p>
      <fieldset class="booking-fields" :disabled="busy">
        <div class="booking-form">
          <label
            >处理方式<select v-model="decision">
              <option value="RESOLVED">{{ approveLabel }}</option>
              <option value="CLOSED">不予办理 / 结束申请</option>
              <option value="PROCESSING">继续沟通</option>
            </select></label
          ><template v-if="changing"
            ><label
              >摄影师<select v-model="photographer" required>
                <option v-for="p in activity.photographers" :key="p.id" :value="p.id">
                  {{ p.name }}
                </option>
              </select></label
            ><label>新拍摄日期<input type="date" v-model="date" :min="today()" required /></label>
            <div class="full">
              <p v-if="loading">正在查询空档…</p>
              <div class="slot-grid">
                <button
                  type="button"
                  v-for="s in slots"
                  :key="s.startTime"
                  :class="{ active: slot === s.startTime }"
                  @click="slot = s.startTime"
                >
                  {{ s.startTime.slice(11, 16) }}
                </button>
              </div>
              <p v-if="!loading && !slots.length">当天暂无可用空档。</p>
            </div></template
          ><label v-if="needsExplanation" class="full"
            >{{ decision === 'CLOSED' ? '不予办理 / 结束原因' : '处理结果'
            }}<textarea v-model="result" maxlength="500" rows="3" required></textarea>
          </label>
        </div>
        <p class="request-notice" v-if="decision === 'RESOLVED' && selected.type === 'CANCEL'">
          确认后取消预约并释放拍摄时段。已有收款仍需单独登记退款。
        </p>
        <p class="request-notice" v-else-if="changing">
          新时段校验通过后才会完成改期；不可用时保留原预约及待处理申请。
        </p>
        <p class="request-notice" v-else-if="decision === 'CLOSED'">
          结束申请并通知客户，原预约安排不变。
        </p>
        <div class="form-actions">
          <button class="primary-button" :disabled="busy || (changing && (!slot || loading))">
            {{ busy ? '正在办理…' : '确认并通知客户' }}</button
          ><button type="button" class="outline-button" @click="selected = null">收起</button>
        </div>
      </fieldset>
    </form>
  </section>
</template>
