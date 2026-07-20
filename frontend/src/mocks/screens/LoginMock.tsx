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
import type { FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import {
  Alert,
  AuthCard,
  Button,
  FormField,
  PasswordInput,
  PublicLayout,
  TextInput,
} from '../../design-system/components'

// デザイン確認用の静的モック。データ連携・業務ロジックは伴わない（FR-0.5）。
// 実装はUNIT-02のFunctional Design/Code Generationで別途行う。
export function LoginMock() {
  const { t } = useTranslation('design-system')
  const { t: tc } = useTranslation()
  const [email, setEmail] = useState('')
  const [failed, setFailed] = useState(false)
  const [loading, setLoading] = useState(false)
  const emailError = email !== '' && !email.includes('@') ? t('mock.login.emailFormat') : undefined

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    setLoading(true)
    setFailed(false)
    // モック: 1秒後に必ず失敗表示する（エラー状態の確認用）
    window.setTimeout(() => {
      setLoading(false)
      setFailed(true)
    }, 1000)
  }

  return (
    <PublicLayout>
      <AuthCard title={t('mock.login.title')}>
        {failed ? <Alert tone="danger">{t('mock.login.failed')}</Alert> : null}
        <form
          onSubmit={onSubmit}
          style={{ display: 'flex', flexDirection: 'column', gap: 'var(--mm-space-3)' }}
        >
          <FormField label={t('mock.login.email')} required error={emailError}>
            <TextInput
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              data-testid="login-mock-email-input"
            />
          </FormField>
          <FormField label={t('mock.login.password')} required>
            <PasswordInput
              autoComplete="current-password"
              data-testid="login-mock-password-input"
            />
          </FormField>
          <Button
            type="submit"
            variant="primary"
            loading={loading}
            data-testid="login-mock-submit-button"
          >
            {tc('action.login')}
          </Button>
        </form>
        <p>
          <Link to="/mock/register">{t('mock.login.register')}</Link>
        </p>
      </AuthCard>
    </PublicLayout>
  )
}
