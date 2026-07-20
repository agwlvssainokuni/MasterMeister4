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

import { useId, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import styles from './FormField.module.css'

export interface FormFieldRenderProps {
  id: string
  'aria-describedby'?: string
  'aria-invalid'?: boolean
}

export interface FormFieldProps {
  label: string
  helperText?: string
  error?: string
  required?: boolean
  /** Renders the field's input, wired up with the id/aria-* props needed for the label and error to be announced. */
  children: (fieldProps: FormFieldRenderProps) => ReactNode
  testId?: string
}

export function FormField({
  label,
  helperText,
  error,
  required,
  children,
  testId,
}: FormFieldProps) {
  const { t } = useTranslation()
  const id = useId()
  const helperId = helperText ? `${id}-helper` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [helperId, errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className={styles.field} data-testid={testId}>
      <label htmlFor={id} className={styles.label}>
        {label}
        {required && <span className={styles.required}> ({t('formField.requiredIndicator')})</span>}
      </label>
      {children({
        id,
        'aria-describedby': describedBy,
        'aria-invalid': error ? true : undefined,
      })}
      {helperText && !error && (
        <p id={helperId} className={styles.helper}>
          {helperText}
        </p>
      )}
      {error && (
        <p id={errorId} className={styles.error} role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
