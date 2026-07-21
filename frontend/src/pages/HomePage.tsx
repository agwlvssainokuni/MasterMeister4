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
import { NAV_ROUTES, PageHeader } from '../design-system/components'
import { AuthenticatedLayout } from './AuthenticatedLayout'
import { FeatureCard } from './FeatureCard'
import styles from './HomePage.module.css'

// frontend-components.md §5。SideNavの8項目に対応するカードグリッド。UNIT-02/03で
// 実装済みの「ユーザ管理」「RDBMS接続設定」のみ活性、他ユニットは「準備中」の非活性カードとする。
const IMPLEMENTED_KEYS = new Set(['users', 'connections'])

export function HomePage() {
  const { t } = useTranslation(['common', 'design-system'])

  return (
    <AuthenticatedLayout>
      <PageHeader title={t('home.welcome')} />
      <div className={styles.grid}>
        {NAV_ROUTES.map((route) => (
          <FeatureCard
            key={route.key}
            title={t(route.labelKey, { ns: 'design-system' })}
            description={t(`home.card.${route.key}`)}
            path={route.path}
            implemented={IMPLEMENTED_KEYS.has(route.key)}
          />
        ))}
      </div>
    </AuthenticatedLayout>
  )
}
