<script setup>
import { useChangeSync } from '../../composables/useChangeSync'

import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getPackage } from '../../api/bookingApi'
import BookingForm from '../../components/BookingForm.vue'
import { money } from '../../utils/bookingFormat'
const route = useRoute(),
  item = ref(null),
  error = ref('')
onMounted(async () => {
  try {
    item.value = await getPackage(route.params.id)
  } catch (e) {
    error.value = e.userMessage
  }
})
useChangeSync({
  publicCatalog: true,
  fetchData: () => getPackage(route.params.id),
  current: () => item.value,
  apply: (value) => {
    item.value = value
  },
  key: () => route.params.id,
})
</script>
<template>
  <RouterLink class="text-button" to="/customer">← 返回套餐</RouterLink>
  <p role="alert" class="error-message" v-if="error">{{ error }}</p>
  <template v-if="item"
    ><div class="page-heading">
      <div>
        <span class="eyebrow">YOUR SESSION</span>
        <h1>{{ item.name }}</h1>
        <p>{{ item.description }}</p>
      </div>
      <strong>¥{{ money(item.price) }}</strong>
    </div>
    <BookingForm :key="item.id" :packageId="item.id"
  /></template>
</template>
