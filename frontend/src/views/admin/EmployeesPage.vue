<script setup>
import { useChangeSync } from '../../composables/useChangeSync'

import { ref, onMounted } from 'vue'
import { get, post, patch } from '../../api/operationsApi'
const items = ref([]),
  form = ref(null),
  id = ref(null),
  error = ref(''),
  notice = ref(''),
  busy = ref(false)
async function load() {
  try {
    items.value = await get('/admin/employees')
  } catch (e) {
    error.value = e.userMessage
  }
}
onMounted(load)
function edit(e) {
  id.value = e?.id || null
  form.value = e
    ? { ...e, roles: e.roles.split(','), password: '' }
    : {
        phone: '',
        name: '',
        employeeNo: '',
        password: '',
        roles: ['RECEPTIONIST'],
        enabled: true,
        bookable: false,
      }
  error.value = ''
  notice.value = ''
}
async function save() {
  busy.value = true
  error.value = ''
  try {
    const body = { ...form.value, password: form.value.password || null }
    await (id.value ? patch('/admin/employees/' + id.value, body) : post('/admin/employees', body))
    notice.value = '员工资料已保存。'
    form.value = null
    await load()
  } catch (e) {
    error.value = e.userMessage
  } finally {
    busy.value = false
  }
}
const roleNames = { ADMIN: '管理员', RECEPTIONIST: '接待', PHOTOGRAPHER: '摄影师' }
useChangeSync({
  fetchData: () => get('/admin/employees'),
  current: () => items.value,
  apply: (value) => {
    items.value = value
    error.value = ''
  },
  paused: () => busy.value || Boolean(form.value),
})
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">OUR TEAM</span>
      <h1>员工与权限</h1>
      <p>每个人各司其职，让接待与拍摄自然衔接。</p>
    </div>
    <button class="primary-button" @click="edit(null)">＋ 添加员工</button>
  </div>
  <p role="alert" class="error-message" v-if="error">{{ error }}</p>
  <p class="success-message" v-if="notice">{{ notice }}</p>
  <section class="panel" v-if="form">
    <h2>{{ id ? '编辑员工' : '添加员工' }}</h2>
    <form @submit.prevent="save">
      <div class="booking-form">
        <label>姓名<input v-model="form.name" maxlength="40" required /></label
        ><label>登录手机号<input v-model="form.phone" pattern="1[3-9][0-9]{9}" required /></label
        ><label>工号<input v-model="form.employeeNo" maxlength="30" required /></label
        ><label
          >{{ id ? '重置密码（留空保持原密码）' : '初始密码'
          }}<input
            type="password"
            v-model="form.password"
            minlength="6"
            maxlength="72"
            :required="!id"
        /></label>
        <fieldset>
          <legend>角色</legend>
          <label class="check"
            ><input type="checkbox" v-model="form.roles" value="RECEPTIONIST" />接待</label
          ><label class="check"
            ><input type="checkbox" v-model="form.roles" value="PHOTOGRAPHER" />摄影师</label
          >
        </fieldset>
        <div>
          <label class="check"><input type="checkbox" v-model="form.enabled" />账号启用</label
          ><label class="check"
            ><input type="checkbox" v-model="form.bookable" />可接受拍摄预约</label
          >
        </div>
      </div>
      <p class="subtle">
        修改员工资料后，该员工需重新登录。停用不会取消已有预约，请先妥善安排未完成的服务。
      </p>
      <div class="form-actions">
        <button class="primary-button" :disabled="busy">保存</button
        ><button type="button" class="outline-button" @click="form = null">取消</button>
      </div>
    </form>
  </section>
  <section class="panel table-scroll">
    <table class="data-table">
      <thead>
        <tr>
          <th>员工</th>
          <th>手机号 / 工号</th>
          <th>角色</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="e in items" :key="e.id">
          <td>
            <strong>{{ e.name }}</strong>
          </td>
          <td>
            {{ e.phone }}<small>{{ e.employeeNo }}</small>
          </td>
          <td>
            {{
              e.roles
                .split(',')
                .map((x) => roleNames[x])
                .join(' / ')
            }}
          </td>
          <td>{{ e.enabled ? '在职' : '停用' }}<small v-if="e.bookable">可预约</small></td>
          <td>
            <button v-if="!e.roles.includes('ADMIN')" class="text-button" @click="edit(e)">
              编辑</button
            ><span v-else>唯一管理账号</span>
          </td>
        </tr>
      </tbody>
    </table>
  </section>
</template>
