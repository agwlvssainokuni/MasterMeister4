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

import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { AppShell, useDefaultNavItems } from '../design-system/components'
import { useAuth } from '../auth/AuthContext'
import { getAccessToken } from '../auth/tokenStorage'
import { decodeJwtEmail, decodeJwtRole } from '../auth/jwt'

// frontend-components.md §6。UNIT-01では「プレースホルダー」だったAppShell Headerの
// ログアウト導線を実装する。管理者専用機能（users/connections/groups/auditLog）の
// ナビ項目は一般ユーザには表示しない（design-system層に認証ロジックを持ち込まないため、
// ロール判定はこのアプリ層で行いisAdminという単純な値のみをuseDefaultNavItemsへ渡す）。
export function AuthenticatedLayout({
  activeNavKey,
  children,
}: {
  activeNavKey?: string
  children: ReactNode
}) {
  const { logout } = useAuth()
  const navigate = useNavigate()
  const accessToken = getAccessToken()
  const isAdmin = accessToken ? decodeJwtRole(accessToken) === 'ADMIN' : false
  const navItems = useDefaultNavItems(activeNavKey, { isAdmin })
  const userLabel = accessToken ? (decodeJwtEmail(accessToken) ?? undefined) : undefined

  const onLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <AppShell
      navItems={navItems}
      userLabel={userLabel}
      onLogout={onLogout}
      onHomeClick={() => navigate('/')}
    >
      {children}
    </AppShell>
  )
}
