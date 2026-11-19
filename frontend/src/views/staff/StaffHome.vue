<script setup>
import { auth } from '../../composables/useAuth'

import { today, statusText } from '../../utils/bookingFormat'

import BookingForm from '../../components/BookingForm.vue'

import { useStaffDay } from '../../composables/useStaffDay.js'

const {
  selectedDate,
  clock,
  items,
  loading,
  error,
  creating,
  receptionist,
  photographer,
  dayLabel,
  greeting,
  dateLabel,
  appointments,
  activeCount,
  focusId,
  timeText,
  duration,
  actionFor,
  destination,
  load,
} = useStaffDay()
</script>

<template>
  <div class="staff-day">
    <header class="staff-day-heading">
      <div>
        <span class="eyebrow">A GOOD DAY TO CREATE</span>
        <h1>{{ greeting }}，{{ auth.user?.displayName || '同事' }}。</h1>
        <p v-if="loading">正在整理拍摄安排…</p>
        <p v-else-if="error">拍摄安排暂未加载，请重试。</p>
        <p v-else>
          {{ dayLabel }}有 {{ activeCount }} 场拍摄，{{
            photographer ? '留一点从容给创作。' : '从容接待每一位客人。'
          }}
        </p>
      </div>
      <time class="staff-day-date" :datetime="selectedDate">
        <strong>{{ Number(selectedDate.slice(8, 10)) }}</strong>
        <span>{{ dateLabel }}</span>
      </time>
    </header>

    <div class="staff-day-toolbar">
      <label>工作日期 <input type="date" v-model="selectedDate" aria-label="工作日期" /></label>
      <button
        class="text-button"
        @click="selectedDate = today()"
        :disabled="selectedDate === today()"
      >
        回到今天
      </button>

      <RouterLink class="text-button" to="/staff/appointments">全部预约 ↗</RouterLink>
      <button
        v-if="receptionist"
        class="outline-button staff-day-create"
        @click="creating = !creating"
      >
        {{ creating ? '收起预约' : '＋ 代客预约' }}
      </button>
    </div>
    <BookingForm v-if="creating" staff @created="load" />
    <p v-if="error" role="alert" class="error-message">{{ error }}</p>
    <section class="staff-day-agenda" aria-label="当日拍摄安排" :aria-busy="loading">
      <p v-if="loading" class="staff-day-empty" role="status">正在加载拍摄安排…</p>
      <div v-else-if="!error && !appointments.length" class="staff-day-empty">
        <h2>当天暂无拍摄安排</h2>
        <p>可以查看其他日期，或前往我的排期。</p>
        <RouterLink class="text-button" to="/staff/schedules">查看排期 ↗</RouterLink>
      </div>
      <article
        v-for="appointment in appointments"
        :key="appointment.id"
        class="staff-day-card"
        :class="{ 'is-current': appointment.id === focusId }"
      >
        <time class="staff-day-time" :datetime="appointment.startTime">{{
          timeText(appointment.startTime)
        }}</time>
        <div class="staff-day-content">
          <div class="staff-day-title"><span class="phase-badge">{{
            appointment.pendingRequestType
              ? appointment.pendingRequestType === 'CANCEL'
                ? '取消申请待处理'
                : '改期申请待处理'
              : statusText[appointment.status] || appointment.status
          }}</span>
          <h2>
            <RouterLink :to="'/staff/appointments/' + appointment.id"
              >{{ appointment.customerName }} · {{ appointment.packageName }}</RouterLink
            >
          </h2></div>
          <p class="staff-day-meta">
            {{ appointment.venueName }} · {{ duration(appointment) }}分钟
            <span v-if="receptionist">· {{ appointment.photographerName }}</span>
          </p>
          <blockquote v-if="appointment.id === focusId && appointment.remark">
            {{ appointment.remark }}
          </blockquote>
          <div class="staff-day-footer">
            <span>拍摄时段 {{ timeText(appointment.startTime) }}–{{ timeText(appointment.shootEndTime) }}</span>
          </div>
          <RouterLink class="staff-day-action" :class="actionFor(appointment).action ? 'primary-button' : 'outline-button'" :to="destination(appointment)">{{ actionFor(appointment).label }} →</RouterLink>
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.staff-day {
  max-width: 1100px;
  margin: 0 auto;
}
.staff-day-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 28px;
  padding: 8px 0 30px;
  border-bottom: 1px solid var(--line);
}
.staff-day-heading h1 {
  font-family: 'Songti SC', serif;
  font-size: 34px;
  line-height: 1.5;
  margin: 14px 0 8px;
}
.staff-day-heading p {
  font-size: 14px;
  color: var(--sub);
}
.staff-day-date {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  flex-shrink: 0;
  color: var(--sub);
}
.staff-day-date strong {
  font-family: Georgia, serif;
  font-size: 44px;
  line-height: 1;
  font-weight: 400;
  color: var(--accent);
}
.staff-day-date span {
  font-size: 14px;
}
.staff-day-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 20px 0;
  font-size: 14px;
  color: var(--sub);
}
.staff-day-toolbar label {
  display: flex;
  align-items: center;
  gap: 9px;
}
.staff-day-toolbar input {
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 3px;
  background: #ffffff;
  color: inherit;
  max-width: 100%;
}
.staff-day-create {
  margin-left: auto;
}
.staff-day-agenda {
  display: grid;
  gap: 16px;
}
.staff-day-card {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 22px;
  padding: 22px 24px;
  background: #ffffff;
  border: 1px solid var(--line);
  border-radius: 12px;
}
.staff-day-card.is-current {
  border-color: var(--accent);
}
.staff-day-time {
  font-size: 21px;
  line-height: 1.5;
  font-variant-numeric: tabular-nums;
}
.staff-day-content {
  min-width: 0;
}
.staff-day-content .phase-badge {
  display: inline-block;
  padding: 3px 8px;
  font-size: 14px;
  border-radius: 3px;
}
.staff-day-content h2 {
  font-size: 18px;
  line-height: 1.6;
  margin: 8px 0 5px;
  overflow-wrap: anywhere;
}
.staff-day-meta {
  font-size: 14px;
  color: var(--sub);
}
.staff-day-content blockquote {
  margin: 22px 0 0;
  padding: 15px 18px;
  background: #e5f0f2;
  font-size: 14px;
  line-height: 1.8;
  color: var(--sub);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  border-radius: 3px;
}
.staff-day-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 14px;
}
.staff-day-footer > span {
  font-size: 14px;
  color: var(--sub);
}
.staff-day-detail {
  display: inline-block;
  padding: 10px 0 0;
}
.staff-day-empty {
  padding: 48px 20px;
  text-align: center;
  color: var(--sub);
}
.staff-day-empty h2 {
  font-size: 18px;
  margin-bottom: 10px;
}
.staff-day-empty p {
  font-size: 14px;
  margin-bottom: 14px;
}
@media (max-width: 640px) {
  .staff-day-heading {
    gap: 16px;
    padding-bottom: 24px;
    align-items: flex-start;
  }
  .staff-day-heading h1 {
    font-size: 26px;
  }
  .staff-day-date strong {
    font-size: 36px;
  }
  .staff-day-date span {
    font-size: 14px;
  }
  .staff-day-card {
    grid-template-columns: 58px minmax(0, 1fr);
    gap: 14px;
    padding: 18px;
  }
  .staff-day-time {
    font-size: 18px;
  }
  .staff-day-content h2 {
    font-size: 17px;
  }
  .staff-day-footer {
    flex-wrap: wrap;
    gap: 14px;
  }
  .staff-day-create {
    margin-left: 0;
  }
  .staff-day-toolbar {
    gap: 9px;
  }
}
.staff-day-title{display:flex;align-items:center;gap:12px;flex-wrap:wrap}
.staff-day-title h2{order:-1;margin:0;font-size:19px}
.staff-day-meta{margin-top:6px;line-height:1.7}
.staff-day-action{display:inline-flex;justify-content:center;align-items:center;gap:12px;min-height:40px;padding:9px 16px;border-radius:8px;font-size:14px;flex-shrink:0}
.staff-day-content blockquote{margin-top:14px;padding:12px 16px}
@media(max-width:640px){.staff-day-title{gap:8px}.staff-day-title h2{font-size:18px}.staff-day-footer{gap:10px}.staff-day-action{margin-left:auto}}
 .staff-day-content{display:grid;grid-template-columns:minmax(0,1fr) auto;column-gap:24px}
.staff-day-title,.staff-day-meta,.staff-day-footer{grid-column:1}
.staff-day-action{grid-column:2;grid-row:1 / span 3;align-self:center}
.staff-day-content blockquote{grid-column:1/-1;grid-row:4}
.staff-day-footer{margin-top:6px}
@media(max-width:640px){.staff-day-content{grid-template-columns:minmax(0,1fr)}.staff-day-action{grid-column:1;grid-row:auto;justify-self:start;margin:12px 0 0}.staff-day-content blockquote{grid-row:auto}}
</style>
