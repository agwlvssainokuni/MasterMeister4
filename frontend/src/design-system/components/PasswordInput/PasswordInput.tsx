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
import { useTranslation } from 'react-i18next'

import { IconButton } from '../Button'
import { TextField } from '../TextField'
import type { TextFieldProps } from '../TextField'
import styles from './PasswordInput.module.css'

export function PasswordInput({ testId, style, ...rest }: TextFieldProps) {
  const { t } = useTranslation()
  const [visible, setVisible] = useState(false)

  return (
    <span className={styles.wrapper}>
      <TextField
        type={visible ? 'text' : 'password'}
        style={{ paddingRight: 'var(--mm-space-8)', ...style }}
        testId={testId}
        {...rest}
      />
      <span className={styles.trailing}>
        <IconButton
          size="sm"
          aria-label={visible ? t('form.hidePassword') : t('form.showPassword')}
          aria-pressed={visible}
          onClick={() => setVisible((current) => !current)}
          testId={testId ? `${testId}-toggle-visibility` : undefined}
        >
          {visible ? '🙈' : '👁'}
        </IconButton>
      </span>
    </span>
  )
}
