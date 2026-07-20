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

import styles from './EmptyState.module.css'

export interface EmptyStateProps {
  message?: ReactNode
  action?: ReactNode
  testId?: string
}

export function EmptyState({ message, action, testId }: EmptyStateProps) {
  const { t } = useTranslation()
  return (
    <div className={styles.empty} data-testid={testId}>
      <span className={styles.emptyIcon} aria-hidden="true">
        📦
      </span>
      <p>{message ?? t('state.empty')}</p>
      {action}
    </div>
  )
}
