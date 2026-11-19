import { createRouter, createWebHistory } from 'vue-router'
import { auth, loadUser, selectPortal } from '../composables/useAuth'
import { portalFor, portalRoles } from '../utils/portal'
import { navigation } from '../constants/navigation'
import ResourcesPage from '../views/admin/ResourcesPage.vue'
import SchedulesPage from '../views/admin/SchedulesPage.vue'
import PackageDetail from '../views/customer/PackageDetail.vue'
import AppointmentList from '../components/AppointmentList.vue'
import AppointmentDetail from '../components/AppointmentDetail.vue'
import CustomerLayout from '../layouts/CustomerLayout.vue'
import WorkspaceLayout from '../layouts/WorkspaceLayout.vue'
import CustomerHome from '../views/customer/CustomerHome.vue'
import AdminHome from '../views/admin/DashboardPage.vue'
import KnowledgePage from '../views/admin/KnowledgePage.vue'
import KnowledgeSource from '../views/customer/KnowledgeSource.vue'
import NoticesPage from '../views/customer/NoticesPage.vue'
import RulesPage from '../views/admin/RulesPage.vue'
import EmployeesPage from '../views/admin/EmployeesPage.vue'
import LogsPage from '../views/admin/LogsPage.vue'
import CustomersPage from '../components/CustomersPage.vue'
import RequestsPage from '../components/RequestsPage.vue'
import NotificationsPage from '../components/NotificationsPage.vue'
import SchedulePage from '../views/staff/SchedulePage.vue'
import StaffHome from '../views/staff/StaffHome.vue'
import ModulePage from '../components/ModulePage.vue'
import AgentPage from '../views/agent/AgentPage.vue'
import ProfilePage from '../components/ProfilePage.vue'
const children = (area) => [
  ...navigation[area].slice(1).map(([path, title]) => ({
    path: path.split('/').pop(),
    component: path.endsWith('/knowledge') ? KnowledgePage : path.endsWith('/notices') ? NoticesPage : path.endsWith('/assistant')
      ? AgentPage
      : path.endsWith('/profile')
        ? ProfilePage
        : path.endsWith('/appointments')
          ? AppointmentList
          : area === 'admin' && path.endsWith('/resources')
            ? ResourcesPage
            : area === 'admin' && path.endsWith('/schedules')
              ? SchedulesPage
              : area === 'staff' && path.endsWith('/schedules')
                ? SchedulePage
                : path.endsWith('/requests')
                  ? RequestsPage
                  : path.endsWith('/messages')
                    ? NotificationsPage
                    : path.endsWith('/customers')
                      ? CustomersPage
                      : path.endsWith('/rules')
                        ? RulesPage
                        : path.endsWith('/team')
                          ? EmployeesPage
                          : path.endsWith('/logs')
                            ? LogsPage
                            : ModulePage,
    meta: {
      title,
      ...(area === 'customer' && path.endsWith('/notices') ? { public: true } : {}),
      ...(area === 'staff' && ['customers', 'requests'].includes(path.split('/').pop())
        ? { roles: ['RECEPTIONIST'] }
        : {}),
    },
  })),
  {path:'notices/:id(\\d+)/:version(\\d+)',component:KnowledgeSource,meta:{public:area==='customer'}},
  ...(area !== 'customer' ? [{path:'notices',component:NoticesPage}] : []),
  ...(area === 'staff' ? [{ path: 'appointments', component: AppointmentList }] : []),
  { path: 'appointments/:id', component: AppointmentDetail },
  ...(area === 'customer'
    ? [{ path: 'packages/:id', component: PackageDetail, meta: { public: true } }]
    : []),
]
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/customer' },
    { path: '/login', redirect: '/customer/login' },
    { path: '/register', redirect: '/customer/register' },
    ...['customer', 'admin', 'staff'].map((area) => ({
      path: `/${area}/login`,
      component: () => import('../views/auth/AuthPage.vue'),
      meta: { area, public: true },
    })),
    {
      path: '/customer/register',
      component: () => import('../views/auth/AuthPage.vue'),
      meta: { area: 'customer', public: true },
    },
    {
      path: '/customer',
      component: CustomerLayout,
      meta: { area: 'customer' },
      children: [{ path: '', component: CustomerHome }, ...children('customer')],
    },
    {
      path: '/admin',
      component: WorkspaceLayout,
      meta: { area: 'admin', roles: ['ADMIN'] },
      children: [{ path: '', component: AdminHome }, ...children('admin')],
    },
    {
      path: '/staff',
      component: WorkspaceLayout,
      meta: { area: 'staff', roles: ['RECEPTIONIST', 'PHOTOGRAPHER'] },
      children: [{ path: '', component: StaffHome }, ...children('staff')],
    },
    { path: '/:pathMatch(.*)*', component: ModulePage, meta: { title: '页面不存在' } },
  ],
})
router.beforeEach(async (to) => {
  const area = portalFor(to.path)
  selectPortal(area)
  const restoreLogin = to.path === `/${area}/login` && !to.query.error
  // Recheck protected routes so stale tabs cannot retain an old account after logout.
  if (!to.meta.public || to.path.startsWith('/customer/packages/') || restoreLogin) {
    try {
      await loadUser()
    } catch {
      if (restoreLogin) return true
      return { path: `/${area}/login`, query: { error: 'offline' } }
    }
  }
  if (restoreLogin && auth.user && auth.user.roles.some((role) => portalRoles[area].includes(role)))
    return '/' + area
  if (to.meta.public) return true
  if (auth.user && to.meta.roles && !auth.user.roles.some((role) => to.meta.roles.includes(role)))
    return '/' + area
  const needsUser = area !== 'customer' || to.path !== '/customer'
  if (needsUser && !auth.user) return { path: `/${area}/login`, query: { redirect: to.fullPath } }
  if (auth.user && !auth.user.roles.some((role) => portalRoles[area].includes(role)))
    return { path: `/${area}/login`, query: { error: 'role' } }
})
export default router
