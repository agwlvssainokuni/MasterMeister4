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

import styles from './Alert.module.css'

export type AlertTone = 'info' | 'success' | 'warning' | 'danger'

const alertToneClass: Record<AlertTone, string> = {
  info: styles.alertInfo,
  success: styles.alertSuccess,
  warning: styles.alertWarning,
  danger: styles.alertDanger,
}

export interface AlertProps {
  tone?: AlertTone
  children: ReactNode
  testId?: string
}

export function Alert({ tone = 'info', children, testId }: AlertProps) {
  return (
    <div className={`${styles.alert} ${alertToneClass[tone]}`} role="alert" data-testid={testId}>
      {children}
    </div>
  )
}
