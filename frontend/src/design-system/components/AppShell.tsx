/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { useState } from 'react'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { IconButton } from './Button'
import { Icon } from './Icon'
import { LanguageSwitcher } from './LanguageSwitcher'
import { ThemeToggle } from './ThemeToggle'
import { Footer } from './Footer'
import headerControlStyles from './HeaderControl.module.css'
import styles from './AppShell.module.css'

export interface NavItem {
  key: string
  label: ReactNode
  icon?: ReactNode
  active?: boolean
  onSelect?: () => void
}

export interface AppShellProps {
  navItems: readonly NavItem[]
  /** ログイン中ユーザ表示（プレースホルダー。実装はUNIT-02） */
  userLabel?: ReactNode
  onLogout?: () => void
  headerExtra?: ReactNode
  children: ReactNode
}

export function AppShell({ navItems, userLabel, onLogout, headerExtra, children }: AppShellProps) {
  const { t } = useTranslation(['design-system', 'common'])
  const [collapsed, setCollapsed] = useState(false)

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <div className={styles.headerLeft}>
          <IconButton
            aria-label="menu"
            aria-expanded={!collapsed}
            onClick={() => setCollapsed((current) => !current)}
            data-testid="app-shell-menu-toggle"
          >
            <Icon name="menu" />
          </IconButton>
          <span className={styles.appTitle}>{t('app.name')}</span>
        </div>
        <div className={styles.headerRight}>
          <LanguageSwitcher />
          <ThemeToggle />
          {userLabel ? (
            <span className={headerControlStyles.userMenuTrigger}>
              <Icon name="user" />
              <span className={styles.userInfo}>{userLabel}</span>
              <IconButton
                aria-label={t('action.logout', { ns: 'common' })}
                onClick={onLogout}
                data-testid="app-shell-logout-button"
              >
                <Icon name="logout" />
              </IconButton>
            </span>
          ) : null}
          {headerExtra}
        </div>
      </header>
      <div className={styles.lower}>
        <nav
          className={`${styles.sidenav} ${collapsed ? styles.sidenavCollapsed : ''}`}
          aria-label="main"
          data-testid="app-shell-sidenav"
        >
          <ul className={styles.navList}>
            {navItems.map((item) => (
              <li key={item.key}>
                <button
                  type="button"
                  className={`${styles.navItem} ${item.active ? styles.navItemActive : ''}`}
                  aria-current={item.active ? 'page' : undefined}
                  onClick={item.onSelect}
                  title={collapsed ? String(item.label) : undefined}
                  data-testid={`app-shell-nav-${item.key}`}
                >
                  <span className={styles.navIcon} aria-hidden="true">
                    {item.icon ?? '▪'}
                  </span>
                  {!collapsed ? <span className={styles.navLabel}>{item.label}</span> : null}
                </button>
              </li>
            ))}
          </ul>
        </nav>
        <main className={styles.content}>{children}</main>
      </div>
      <Footer />
    </div>
  )
}
