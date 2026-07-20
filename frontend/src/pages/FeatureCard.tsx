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
import { useNavigate } from 'react-router-dom'
import { Badge, Card } from '../design-system/components'
import styles from './FeatureCard.module.css'

export interface FeatureCardProps {
  title: ReactNode
  description: ReactNode
  path: string
  implemented: boolean
}

// frontend-components.md §5。implemented: false のカードはクリック不可・非活性表示とし、
// 「準備中」バッジを付与する（SideNavの未実装ユニット表現と一貫性を取る）。
export function FeatureCard({ title, description, path, implemented }: FeatureCardProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const cardTitle = (
    <span className={styles.cardTitle}>
      <span aria-hidden="true">▪</span>
      {title}
    </span>
  )

  if (!implemented) {
    return (
      <div className={styles.cardDisabled} aria-disabled="true">
        <Card title={cardTitle}>
          <p>{description}</p>
          <Badge>{t('home.comingSoon')}</Badge>
        </Card>
      </div>
    )
  }

  return (
    <button
      type="button"
      className={styles.cardButton}
      onClick={() => navigate(path)}
      data-testid={`feature-card-${path.replace(/\//g, '')}`}
    >
      <Card title={cardTitle}>
        <p>{description}</p>
      </Card>
    </button>
  )
}
