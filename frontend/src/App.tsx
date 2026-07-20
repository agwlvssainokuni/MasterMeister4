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

import { Button, Checkbox, FormField, RadioButton, Select, TextField } from './design-system'
import styles from './App.module.css'

const RDBMS_OPTIONS = [
  { value: 'mysql', label: 'MySQL' },
  { value: 'mariadb', label: 'MariaDB' },
  { value: 'postgresql', label: 'PostgreSQL' },
  { value: 'h2', label: 'H2 Database' },
]

// Placeholder screen for UNIT-01 (STORY-0.1): proves the common UI
// components render and behave correctly. Real feature screens are built
// per-unit starting with UNIT-02 (FR-0.3 — no upfront, all-screens mockup).
function App() {
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const [rdbms, setRdbms] = useState('mysql')
  const [rememberMe, setRememberMe] = useState(false)
  const [dbEngine, setDbEngine] = useState('mysql')

  return (
    <main className={styles.page}>
      <h1 className={styles.title}>MasterMeister</h1>

      <FormField label="メールアドレス" helperText="登録済みのメールアドレスを入力してください">
        {(fieldProps) => (
          <TextField
            {...fieldProps}
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            testId="email-field-input"
          />
        )}
      </FormField>

      <FormField label="対象RDBMS">
        {(fieldProps) => (
          <Select
            {...fieldProps}
            options={RDBMS_OPTIONS}
            value={rdbms}
            onChange={(event) => setRdbms(event.target.value)}
            testId="rdbms-select-input"
          />
        )}
      </FormField>

      <Checkbox
        label="ログイン状態を保持する"
        checked={rememberMe}
        onChange={(event) => setRememberMe(event.target.checked)}
        testId="remember-me-checkbox"
      />

      <fieldset>
        <legend>{t('formField.requiredIndicator')}: DBエンジン</legend>
        {RDBMS_OPTIONS.map((option) => (
          <RadioButton
            key={option.value}
            label={option.label}
            name="db-engine"
            value={option.value}
            checked={dbEngine === option.value}
            onChange={() => setDbEngine(option.value)}
            testId={`db-engine-${option.value}-radio`}
          />
        ))}
      </fieldset>

      <pre className={styles.sqlSample}>{'SELECT id, name FROM 顧客 WHERE status = ?'}</pre>

      <div className={styles.actions}>
        <Button variant="primary" testId="app-submit-button">
          送信
        </Button>
        <Button variant="secondary" testId="app-cancel-button">
          キャンセル
        </Button>
      </div>
    </main>
  )
}

export default App
