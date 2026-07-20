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
import { useTranslation } from 'react-i18next'
import { LanguageSwitcher } from './LanguageSwitcher'
import { ThemeToggle } from './ThemeToggle'
import styles from './PublicLayout.module.css'

export interface PublicLayoutProps {
  children: ReactNode
}

// ログイン・ユーザ登録画面用の最小シェル（ロゴ＋コンテンツのみ）。
// AppShell（ヘッダー/サイドナビ/フッター）は未ログイン時には適用しない。
export function PublicLayout({ children }: PublicLayoutProps) {
  const { t } = useTranslation('design-system')
  return (
    <div className={styles.shell}>
      <div className={styles.logo}>{t('app.name')}</div>
      <div className={styles.content}>{children}</div>
      <div className={styles.controls}>
        <LanguageSwitcher />
        <ThemeToggle />
      </div>
    </div>
  )
}
