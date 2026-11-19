<script setup>
import RequestPanel from './RequestPanel.vue'

import { readableContent } from '../utils/workflow'

import { getPortal } from '../utils/portal'

import { dateText, money, statusText, today } from '../utils/bookingFormat'

import { useAppointmentDetail } from '../composables/useAppointmentDetail.js'

const {
  requestEditing,
  item,
  activity,
  error,
  notice,
  busy,
  mode,
  reason,
  amount,
  method,
  original,
  requestType,
  date,
  photographer,
  slots,
  selected,
  notes,
  slotBusy,
  customer,
  desk,
  shooter,
  paid,
  actions,
  methods,
  load,
  open,
  loadSlots,
  submit,
  saveNotes,
} = useAppointmentDetail()
</script>
<template>
  <RouterLink class="text-button" :to="'/' + getPortal() + '/appointments'"
    >← 返回预约列表</RouterLink
  >
  <p role="alert" class="error-message" v-if="error">{{ error }}</p>
  <p class="success-message" v-if="notice">{{ notice }}</p>
  <template v-if="item && activity"
    ><div class="page-heading">
      <div>
        <span class="eyebrow">BOOKING DETAILS</span>
        <h1>{{ item.packageName }}</h1>
        <p>{{ item.appointmentNo }}</p>
      </div>
      <span class="phase-badge">{{ statusText[item.status] || item.status }}</span>
    </div>
    <div class="detail-grid">
      <section class="panel">
        <h2>拍摄安排</h2>
        <dl class="detail-list">
          <dt>拍摄时间</dt>
          <dd>{{ dateText(item.startTime) }} 至 {{ item.shootEndTime.slice(11, 16) }}</dd>
          <dt>摄影师 / 场地</dt>
          <dd>{{ item.photographerName }} · {{ item.venueName }}</dd>
          <dt>客户</dt>
          <dd>{{ item.customerName }} · {{ item.customerPhone }}</dd>
          <dt>应收金额</dt>
          <dd>¥{{ money(item.amount) }}</dd>
          <dt>备注</dt>
          <dd>{{ item.remark || '无' }}</dd>
        </dl>
        <div class="form-actions">
          <a
            v-if="desk && activity.pendingChange"
            class="primary-button"
            href="#appointment-requests"
            >处理申请</a
          ><template
            v-if="
              item.status === 'CONFIRMED' &&
              !activity.pendingChange &&
              (desk || (customer && activity.selfChangeAllowed))
            "
            ><button class="outline-button" @click="open('reschedule')">改期</button
            ><button class="outline-button" @click="open('cancel')">取消预约</button></template
          ><button
            v-if="customer && !activity.pendingChange"
            class="outline-button"
            @click="open('request')"
          >
            申请人工处理</button
          ><template v-if="desk && !activity.pendingChange && item.status === 'CONFIRMED'"
            ><button class="primary-button" @click="open('check-in')">登记到店</button
            ><button class="text-button" @click="open('mark-no-show')">标记爽约</button></template
          ><button
            v-if="shooter && item.status === 'CHECKED_IN'"
            class="primary-button"
            @click="open('start')"
          >
            开始服务</button
          ><button
            v-if="shooter && item.status === 'IN_PROGRESS'"
            class="primary-button"
            @click="open('complete')"
          >
            完成服务
          </button>
        </div>
        <p class="subtle" v-if="activity.pendingChange">
          取消或改期申请待处理，原拍摄时段暂时保留，请先完成申请处理。
        </p>
        <p
          class="subtle"
          v-if="customer && item.status === 'CONFIRMED' && !activity.selfChangeAllowed"
        >
          已超过自助办理时限，可提交人工申请。
        </p>
      </section>
      <section class="panel" v-if="activity.received !== undefined">
        <h2>收款情况</h2>
        <div class="payment-total">
          ¥{{ money(activity.net) }} <small>净收款 / 应收 ¥{{ money(item.amount) }}</small>
        </div>
        <p>
          <span class="phase-badge">{{ activity.collectionStatus }}</span>
          <span class="phase-badge">{{ activity.refundStatus }}</span>
        </p>
        <p class="subtle">
          累计收款 ¥{{ money(activity.received) }} · 累计退款 ¥{{ money(activity.refunded) }}
        </p>
        <div class="form-actions">
          <button
            class="primary-button"
            v-if="
              desk &&
              Number(activity.net) < Number(item.amount) &&
              ['CHECKED_IN', 'IN_PROGRESS', 'COMPLETED'].includes(item.status)
            "
            @click="open('payment')"
          >
            登记收款</button
          ><button v-if="desk && paid.length" class="outline-button" @click="open('refund')">
            登记退款
          </button>
        </div>
        <p class="subtle">取消预约不会自动退款；已登记的流水保留记录。</p>
      </section>
    </div>
    <RequestPanel
      v-if="activity.requests?.length"
      :key="item.id"
      :item="item"
      :activity="activity"
      :editable="desk"
      @editing="requestEditing = $event"
      @updated="load"
    />
    <section class="panel action-panel" v-if="mode">
      <div class="section-title">
        <h2>{{ actions[mode] }}</h2>
        <button class="text-button" :disabled="busy" @click="mode = ''">收起</button>
      </div>
      <form @submit.prevent="submit">
        <fieldset class="booking-fields" :disabled="busy">
          <div class="booking-form">
            <template v-if="mode === 'reschedule'"
              ><label
                >摄影师<select v-model="photographer" required>
                  <option v-for="p in activity.photographers" :value="p.id">{{ p.name }}</option>
                </select></label
              ><label>新拍摄日期<input type="date" v-model="date" :min="today()" required /></label>
              <div class="full">
                <p v-if="slotBusy">正在查询空档…</p>
                <div class="slot-grid">
                  <button
                    type="button"
                    v-for="s in slots"
                    :class="{ active: selected === s.startTime }"
                    @click="selected = s.startTime"
                  >
                    {{ s.startTime.slice(11, 16) }}
                  </button>
                </div>
                <p v-if="!slotBusy && !slots.length">当天暂无可用空档。</p>
                <small>改期保留原套餐、价格及拍摄时长。</small>
              </div></template
            ><template v-if="mode === 'payment' || mode === 'refund'"
              ><label
                >金额（元）<input
                  type="number"
                  v-model="amount"
                  min="0.01"
                  step="0.01"
                  :max="
                    mode === 'refund'
                      ? (original?.remainingCents || 0) / 100
                      : Math.max(0, Number(item.amount) - Number(activity.net))
                  "
                  required /></label
              ><label v-if="mode === 'payment'"
                >收款方式<select v-model="method">
                  <option v-for="(label, key) in methods" :value="key">{{ label }}</option>
                </select></label
              ><label v-if="mode === 'refund'"
                >原收款流水<select v-model="original" required>
                  <option v-for="p in paid" :key="p.id" :value="p">
                    #{{ p.id }} · {{ methods[p.method] }} · 可退 ¥{{
                      money(p.remainingCents / 100)
                    }}
                  </option>
                </select></label
              ></template
            ><label v-if="mode === 'request'"
              >申请类型<select v-model="requestType">
                <option v-if="item.status === 'CONFIRMED'" value="RESCHEDULE">希望改期</option>
                <option v-if="item.status === 'CONFIRMED'" value="CANCEL">希望取消</option>
                <option value="OTHER">其他问题</option>
              </select></label
            ><label v-if="!['check-in', 'payment', 'start', 'complete'].includes(mode)" class="full"
              >{{ mode === 'request' ? '具体诉求' : mode === 'refund' ? '退款原因' : '办理说明'
              }}<textarea
                v-model="reason"
                :maxlength="mode === 'request' ? 1000 : 500"
                rows="3"
                required
              ></textarea>
            </label>
          </div>
          <p class="subtle" v-if="mode === 'cancel'">
            确认后会释放当前预约的拍摄时段，已有收款不会自动退回。
          </p>
          <div class="form-actions">
            <button class="primary-button" :disabled="busy || (mode === 'reschedule' && !selected)">
              {{ busy ? '正在办理…' : '确认' + actions[mode] }}</button
            ><button type="button" class="outline-button" @click="mode = ''">返回</button>
          </div>
        </fieldset>
      </form>
    </section>
    <section class="panel" v-if="activity.service">
      <h2>服务记录</h2>
      <div class="service-timeline">
        <span>到店 {{ dateText(activity.service.checkedInAt) || '—' }}</span
        ><span>开始 {{ dateText(activity.service.startedAt) || '—' }}</span
        ><span>完成 {{ dateText(activity.service.completedAt) || '—' }}</span>
      </div>
      <form v-if="!customer" @submit.prevent="saveNotes">
        <label class="stack-label"
          >服务备注<textarea v-model="notes" maxlength="1000" rows="3"></textarea></label
        ><button class="outline-button" :disabled="busy">保存备注</button>
      </form>
      <p v-else>{{ activity.service.notes || '暂无服务备注' }}</p>
    </section>
    <section class="panel" v-if="activity.payments?.length">
      <h2>收退款流水</h2>
      <div class="table-scroll">
        <table class="data-table">
          <thead>
            <tr>
              <th>流水 / 时间</th>
              <th>类型</th>
              <th>金额 / 方式</th>
              <th>说明</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in activity.payments" :key="p.id">
              <td>
                #{{ p.id }}<small>{{ dateText(p.createdAt) }}</small>
              </td>
              <td>
                {{ p.type === 'PAYMENT' ? '收款' : '退款'
                }}<small v-if="p.originalId">原流水 #{{ p.originalId }}</small>
              </td>
              <td>
                ¥{{ money(p.amount) }}<small>{{ methods[p.method] }}</small>
              </td>
              <td>{{ p.reason }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
    <section class="panel">
      <h2>办理记录</h2>
      <div class="message-row" v-for="l in activity.logs" :key="l.id">
        <div>
          <p>{{ readableContent(l.detail) }}</p>
          <small>{{ l.operator }} · {{ dateText(l.createdAt) }}</small>
        </div>
      </div>
    </section></template
  >
</template>
