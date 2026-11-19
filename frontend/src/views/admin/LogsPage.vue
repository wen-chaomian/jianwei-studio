<script setup>
import { usePagedList } from '../../composables/usePagedList'
import ListPager from '../../components/ListPager.vue'
import { dateText } from '../../utils/bookingFormat'
const { items, total, page, loading, error, load, turn } = usePagedList('/admin/operation-logs')
const names = {
  CREATE: '创建',
  UPDATE: '修改',
  SAVE: '保存',
  INITIALIZE: '初始化',
  RESET_PASSWORD: '重置密码',
  APPOINTMENT: '预约',
  EMPLOYEE: '员工',
  PROFILE: '个人资料',
  CUSTOMER: '客户',
  RULE: '业务规则',
  REQUEST: '人工申请',
  STUDIO: '工作室',
  reschedule: '改期',
  cancel: '取消',
  payment: '收款',
  refund: '退款',
  'check-in': '到店',
  start: '开始服务',
  complete: '完成服务',
  'mark-no-show': '爽约',
  'service-notes': '服务备注',
  PROCESSING: '处理中',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
}
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">ACTIVITY HISTORY</span>
      <h1>操作日志</h1>
      <p>关键业务变动均保留记录，便于核对与追溯。</p>
    </div>
  </div>
  <section class="panel">
    <p class="error-message" v-if="error">{{ error }}</p>
    <div class="table-scroll">
      <table class="data-table">
        <thead>
          <tr>
            <th>时间</th>
            <th>操作人</th>
            <th>模块 / 操作</th>
            <th>内容</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="l in items" :key="l.id">
            <td>{{ dateText(l.created_at) }}</td>
            <td>{{ l.operatorName || l.operator_id }}</td>
            <td>{{ names[l.module] || l.module }} / {{ names[l.action] || l.action }}</td>
            <td>{{ l.detail }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <ListPager :page="page" :total="total" :loading="loading" @turn="turn" />
  </section>
</template>
