<script setup>
import BrandLogo from '../components/BrandLogo.vue'
import { navigation } from '../constants/navigation'
import { auth, logout } from '../composables/useAuth'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
const router = useRouter()
async function signOut() {
  try {
    await logout()
    router.push('/customer')
  } catch (e) {
    ElMessage.error(e.userMessage)
  }
}
</script>
<template>
  <div class="customer-shell">
    <header class="site-header">
      <BrandLogo />
      <nav>
        <RouterLink v-for="[path, title] in navigation.customer" :key="path" :to="path">{{
          title
        }}</RouterLink>
      </nav>
      <div class="account-actions" v-if="auth.user">
        <span>{{ auth.user.displayName }}</span
        ><button class="text-button" @click="signOut">退出</button>
      </div>
      <RouterLink v-else class="outline-button" to="/customer/login">登录 / 注册</RouterLink>
    </header>
    <main class="customer-main"><RouterView /></main>
    <footer>见微摄影工作室 <span>留住此刻，慢慢喜欢。</span></footer>
  </div>
</template>
