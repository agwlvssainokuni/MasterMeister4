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

import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import type { NavItem } from './AppShell'

// 全10ユニットを見込んだナビ項目一式（frontend-components.md §1.3、UNIT-02で
// 「ダッシュボード」を「ユーザ管理」に統合。レビュー指摘の反映）。
// リンク先は各ユニット未着手の間はプレースホルダー（実装済みユニットのみ実際に遷移可能）。
export const NAV_ROUTES = [
  { key: 'users', labelKey: 'nav.users', path: '/users' },
  { key: 'connections', labelKey: 'nav.connections', path: '/connections' },
  { key: 'groups', labelKey: 'nav.groups', path: '/groups' },
  { key: 'masterData', labelKey: 'nav.masterData', path: '/master-data' },
  { key: 'savedQueries', labelKey: 'nav.savedQueries', path: '/saved-queries' },
  { key: 'queryBuilder', labelKey: 'nav.queryBuilder', path: '/query-builder' },
  { key: 'queryHistory', labelKey: 'nav.queryHistory', path: '/query-history' },
  { key: 'auditLog', labelKey: 'nav.auditLog', path: '/audit-log' },
] as const

export function useDefaultNavItems(activeKey?: string): NavItem[] {
  const { t } = useTranslation('design-system')
  const navigate = useNavigate()

  return NAV_ROUTES.map((route) => ({
    key: route.key,
    label: t(route.labelKey),
    active: route.key === activeKey,
    onSelect: () => navigate(route.path),
  }))
}
