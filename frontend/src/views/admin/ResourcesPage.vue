<script setup>
import { useChangeSync } from '../../composables/useChangeSync'

import { ref, onMounted } from 'vue'
import * as api from '../../api/bookingApi'
import { money } from '../../utils/bookingFormat'
const packages = ref([]),
  venues = ref([]),
  photographers = ref([]),
  error = ref(''),
  message = ref(''),
  busy = ref(false),
  tab = ref('packages'),
  editing = ref(null),
  form = ref({})
async function load() {
  try {
    ;[packages.value, venues.value, photographers.value] = await Promise.all([
      api.getPackages(true),
      api.getVenues(),
      api.getPhotographers(),
    ])
  } catch (e) {
    error.value = e.userMessage
  }
}
function edit(item = null) {
  error.value = ''
  message.value = ''
  editing.value = tab.value
  if (tab.value === 'packages')
    form.value = item
      ? {
          ...item,
          photographerIds: item.photographers.map((x) => x.id),
          venueIds: item.venues.map((x) => x.id),
        }
      : {
          name: '',
          description: '',
          price: 699,
          durationMinutes: 90,
          bufferMinutes: 30,
          enabled: true,
          photographerIds: [],
          venueIds: [],
        }
  else form.value = item ? { ...item } : { name: '', location: '', description: '', enabled: true }
}
async function save() {
  busy.value = true
  error.value = ''
  try {
    if (editing.value === 'packages') await api.savePackage(form.value.id, form.value)
    else await api.saveVenue(form.value.id, form.value)
    editing.value = null
    message.value = '已保存'
    await load()
  } catch (e) {
    error.value = e.userMessage
  } finally {
    busy.value = false
  }
}
async function toggle(item) {
  busy.value = true
  try {
    await api.setBookable(item.id, !item.bookable)
    await load()
  } catch (e) {
    error.value = e.userMessage
  } finally {
    busy.value = false
  }
}
function selectTab(key) { tab.value = key; editing.value = null }
onMounted(load)
useChangeSync({
  fetchData: () => Promise.all([api.getPackages(true), api.getVenues(), api.getPhotographers()]),
  current: () => [packages.value, venues.value, photographers.value],
  apply: (value) => {
    ;[packages.value, venues.value, photographers.value] = value
    error.value = ''
  },
  paused: () => busy.value || Boolean(editing.value),
})
</script>
<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">SERVICES & RESOURCES</span>
      <h1>套餐与资源</h1>
      <p>先配置场地与摄影师，再为套餐关联可用资源。</p>
    </div>
    <button v-if="tab !== 'photographers'" class="primary-button" @click="edit()">
      ＋ 新建{{ tab === 'packages' ? '套餐' : '场地' }}
    </button>
  </div>
  <div class="booking-tabs">
    <button
      v-for="[key, label] in [
        ['packages', '拍摄套餐'],
        ['venues', '场地'],
        ['photographers', '摄影师'],
      ]"
      :class="{ active: tab === key }"
      @click="selectTab(key)"
    >
      {{ label }}
    </button>
  </div>
  <p class="error-message" role="alert" v-if="error">{{ error }}</p>
  <p class="success-message" v-if="message">{{ message }}</p>
  <section v-if="editing" class="panel">
    <h2>{{ form.id ? '编辑' : '新建' }}{{ editing === 'packages' ? '套餐' : '场地' }}</h2>
    <form class="booking-form" @submit.prevent="save">
      <label>名称<input v-model="form.name" required maxlength="80" /></label
      ><label v-if="editing === 'venues'"
        >位置<input v-model="form.location" maxlength="200" /></label
      ><label class="full"
        >介绍<textarea
          v-model="form.description"
          :maxlength="editing === 'packages' ? 1000 : 500"
          rows="3"
        ></textarea></label
      ><template v-if="editing === 'packages'"
        ><label
          >价格（元）<input
            v-model.number="form.price"
            type="number"
            min="0"
            max="99999999.99"
            step="0.01"
            required /></label
        ><label
          >拍摄时长（分钟）<input
            v-model.number="form.durationMinutes"
            type="number"
            min="30"
            max="480"
            step="30"
            required /></label
        ><label
          >缓冲时长（分钟）<input
            v-model.number="form.bufferMinutes"
            type="number"
            min="0"
            max="120"
            step="30"
            required
        /></label>
        <fieldset>
          <legend>可选摄影师</legend>
          <label
            class="check-label"
            v-for="p in photographers.filter((x) => x.enabled && x.bookable)"
            ><input type="checkbox" v-model="form.photographerIds" :value="p.id" />{{
              p.name
            }}</label
          ><small v-if="!photographers.some((x) => x.enabled && x.bookable)"
            >暂无可预约摄影师，请先启用。</small
          >
        </fieldset>
        <fieldset>
          <legend>可用场地</legend>
          <label class="check-label" v-for="v in venues.filter((x) => x.enabled)"
            ><input type="checkbox" v-model="form.venueIds" :value="v.id" />{{ v.name }}</label
          ><small v-if="!venues.some((x) => x.enabled)">请先新建场地。</small>
        </fieldset></template
      ><label class="check-label"><input type="checkbox" v-model="form.enabled" />启用</label>
      <div class="form-actions full">
        <button class="primary-button" :disabled="busy">{{ busy ? '保存中…' : '保存' }}</button
        ><button type="button" class="outline-button" @click="editing = null">取消</button>
      </div>
    </form>
  </section>
  <section class="panel resource-list" v-if="tab === 'packages'">
    <article v-for="p in packages" :key="p.id">
      <div>
        <h3>
          {{ p.name }} <small>{{ p.enabled ? '已上架' : '已下架' }}</small>
        </h3>
        <p>{{ p.description }}</p>
        <small
          >{{ p.durationMinutes }}分钟拍摄 + {{ p.bufferMinutes }}分钟缓冲 ·
          {{ p.photographers.map((x) => x.name).join('、') || '未关联摄影师' }} ·
          {{ p.venues.map((x) => x.name).join('、') || '未关联场地' }}</small
        >
      </div>
      <strong>¥{{ money(p.price) }}</strong
      ><button class="outline-button" @click="edit(p)">编辑</button>
    </article>
    <div class="empty-state" v-if="!packages.length">暂无套餐，先配置资源后新建。</div>
  </section>
  <section class="panel resource-list" v-if="tab === 'venues'">
    <article v-for="v in venues" :key="v.id">
      <div>
        <h3>
          {{ v.name }} <small>{{ v.enabled ? '已启用' : '已停用' }}</small>
        </h3>
        <p>{{ v.location }}</p>
        <small>{{ v.description }}</small>
      </div>
      <button class="outline-button" @click="edit(v)">编辑</button>
    </article>
    <div class="empty-state" v-if="!venues.length">暂无场地，请点击新建。</div>
  </section>
  <section class="panel resource-list" v-if="tab === 'photographers'">
    <article v-for="p in photographers" :key="p.id">
      <div>
        <h3>{{ p.name }}</h3>
        <small>{{ p.bookable ? '可预约' : '暂停预约' }} · {{ p.enabled ? '在职' : '停用' }}</small>
      </div>
      <button class="outline-button" :disabled="busy" @click="toggle(p)">
        {{ p.bookable ? '暂停预约' : '允许预约' }}
      </button>
    </article>
    <small>这里设置现有摄影师是否可预约。员工账号创建在员工权限模块后续接入。</small>
  </section>
</template>
