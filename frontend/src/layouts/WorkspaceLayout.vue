<script setup>
import { computed } from 'vue'
import BrandLogo from '../components/BrandLogo.vue'
import { navigation } from '../constants/navigation'
import { auth, logout } from '../composables/useAuth'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
const route = useRoute(),
  router = useRouter()
const links = computed(() =>
  navigation[route.meta.area].filter(
    ([path]) =>
      !(
        route.meta.area === 'staff' &&
        !auth.user?.roles.includes('RECEPTIONIST') &&
        (path.endsWith('/customers') || path.endsWith('/requests'))
      ),
  ),
)
async function signOut() {
  try {
    await logout()
    router.push('/' + route.meta.area + '/login')
  } catch (e) {
    ElMessage.error(e.userMessage)
  }
}
</script>
<template>
  <div class="workspace">
    <aside>
      <BrandLogo />
      <p class="sidebar-caption">{{ route.meta.area === 'admin' ? '商家管理' : '员工工作台' }}</p>
      <nav>
        <RouterLink v-for="[path, title] in links" :key="path" :to="path"
          ><span>{{ title }}</span
          ><span>↗</span></RouterLink
        >
      </nav>
      <div class="sidebar-bottom"><small>把每一次相遇，认真对待。</small></div>
    </aside>
    <div class="workspace-body">
      <header class="workspace-header">
        <span>见微 · {{ route.meta.area === 'admin' ? '经营与服务' : '今日工作' }}</span>
        <div>
          {{ auth.user?.displayName }}<button class="text-button" @click="signOut">退出登录</button>
        </div>
      </header>
      <main><RouterView /></main>
    </div>
  </div>
</template>
