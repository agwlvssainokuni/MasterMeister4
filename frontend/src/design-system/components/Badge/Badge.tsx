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

import styles from './Badge.module.css'

export type BadgeTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger'

const badgeToneClass: Record<BadgeTone, string> = {
  neutral: styles.badgeNeutral,
  primary: styles.badgePrimary,
  success: styles.badgeSuccess,
  warning: styles.badgeWarning,
  danger: styles.badgeDanger,
}

export interface BadgeProps {
  tone?: BadgeTone
  children: ReactNode
  testId?: string
}

export function Badge({ tone = 'neutral', children, testId }: BadgeProps) {
  return (
    <span className={`${styles.badge} ${badgeToneClass[tone]}`} data-testid={testId}>
      {children}
    </span>
  )
}
