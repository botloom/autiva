<template>
  <div class="app-layout">
    <nav class="app-sidebar">
      <div class="sidebar-brand">Autiva</div>
      <div class="sidebar-nav">
        <router-link to="/" class="sidebar-link" :class="{ 'sidebar-link--active': $route.path === '/' }">
          <svg width="16" height="16" viewBox="0 0 18 18" fill="none">
            <rect x="2" y="2" width="6" height="6" rx="1.5" stroke="currentColor" stroke-width="1.5"/>
            <rect x="10" y="2" width="6" height="6" rx="1.5" stroke="currentColor" stroke-width="1.5"/>
            <rect x="2" y="10" width="6" height="6" rx="1.5" stroke="currentColor" stroke-width="1.5"/>
            <rect x="10" y="10" width="6" height="6" rx="1.5" stroke="currentColor" stroke-width="1.5"/>
          </svg>
          项目
        </router-link>
        <router-link to="/notifications" class="sidebar-link" :class="{ 'sidebar-link--active': $route.path === '/notifications' }">
          <svg width="16" height="16" viewBox="0 0 18 18" fill="none">
            <path d="M9 2a5 5 0 015 5v3l2 2H2l2-2V7a5 5 0 015-5z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/>
            <path d="M7.5 14a1.5 1.5 0 003 0" stroke="currentColor" stroke-width="1.5"/>
          </svg>
          通知
        </router-link>
      </div>
    </nav>
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
}

.app-sidebar {
  width: var(--sidebar-width);
  background: var(--color-bg-card);
  border-right: 1px solid var(--color-border-light);
  display: flex;
  flex-direction: column;
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: var(--z-sticky);
  padding: var(--space-5) var(--space-3);
}

.sidebar-brand {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-bold);
  color: var(--color-primary);
  letter-spacing: -0.02em;
  padding: 0 var(--space-3);
  margin-bottom: var(--space-5);
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.sidebar-link {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-secondary);
  transition: all var(--transition-fast);
}

.sidebar-link:hover {
  background: var(--color-bg-hover);
  color: var(--color-text-primary);
}

.sidebar-link--active {
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.app-main {
  flex: 1;
  margin-left: var(--sidebar-width);
  min-height: 100vh;
  background: var(--color-bg-page);
}
</style>
