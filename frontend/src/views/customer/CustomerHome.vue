<script setup>
import { useChangeSync } from '../../composables/useChangeSync'

import { ref, onMounted } from 'vue'
import { getPackages } from '../../api/bookingApi'
import { money } from '../../utils/bookingFormat'
const packages = ref([]),
  error = ref(''),
  loading = ref(true)
onMounted(async () => {
  try {
    packages.value = await getPackages()
  } catch (e) {
    error.value = e.userMessage
  } finally {
    loading.value = false
  }
})
useChangeSync({
  publicCatalog: true,
  fetchData: () => getPackages(),
  current: () => packages.value,
  apply: (value) => {
    packages.value = value
    error.value = ''
  },
  paused: () => loading.value,
})
</script>
<template>
  <section class="hero">
    <div>
      <span class="eyebrow">SLOW DOWN. BE YOURSELF.</span>
      <h1>把平凡的一天，<br />留成喜欢的样子。</h1>
      <p>不用刻意表现，在自然的光里，做你自己。<br />其余的，交给见微。</p>
      <a class="primary-button" href="#sessions">看看拍摄套餐 <span>↗</span></a>
    </div>
    <div class="light-art">
      <i></i><b></b><span>JIANWEI / THE ART OF EVERYDAY</span><small>自然，真实，留住此刻。</small>
    </div>
  </section>
  <section id="sessions">
    <div class="section-heading">
      <div>
        <span class="eyebrow">OUR SESSIONS</span>
        <h2>为不同的此刻，准备。</h2>
      </div>
      <small>选择套餐，查看摄影师与可预约时间</small>
    </div>
    <p v-if="error" class="error-message" role="alert">{{ error }}</p>
    <p v-if="loading">正在加载套餐…</p>
    <p v-if="!loading && !packages.length" class="empty-state">暂无上架套餐，敬请期待。</p>
    <div class="package-grid">
      <article v-for="(item, index) in packages" :key="item.name" class="package-card">
        <span class="package-number">0{{ index + 1 }}</span
        ><small>PHOTOGRAPHY SESSION</small>
        <h3>{{ item.name }}</h3>
        <p>{{ item.durationMinutes }}分钟拍摄 · {{ item.description }}</p>
        <div class="package-bottom">
          <strong>¥{{ money(item.price) }}</strong
          ><span>到店付款</span>
        </div>
        <RouterLink class="outline-button package-book" :to="'/customer/packages/' + item.id"
          >查看套餐与预约 ↗</RouterLink
        >
      </article>
    </div>
  </section>
  <div class="customer-note">
    <span>每一段故事，都从一次相遇开始。</span>
    <p>线上预约，到店付款。选择适合你的拍摄时光。</p>
  </div>
</template>
