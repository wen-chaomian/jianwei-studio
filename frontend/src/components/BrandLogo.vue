<script setup>
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
<template>
  <RouterLink class="brand" :to="'/' + (route.meta.area || 'customer')"
    >见微<span>JIANWEI STUDIO</span></RouterLink
  >
</template>
