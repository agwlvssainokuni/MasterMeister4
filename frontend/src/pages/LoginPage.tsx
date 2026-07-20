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
import { Link, useNavigate } from 'react-router-dom'
import {
  Alert,
  AuthCard,
  Button,
  FormField,
  PasswordInput,
  PublicLayout,
  TextInput,
} from '../design-system/components'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/http'

const formStyle = { display: 'flex', flexDirection: 'column' as const, gap: 'var(--mm-space-3)' }

// frontend-components.md §1。BR-REG-04によりメールアドレス不存在・パスワード不一致は
// 同一メッセージ（AUTH_INVALID_CREDENTIALS）。サーバがAccept-Languageに応じて
// メッセージを生成するため、ApiError.messageをそのまま表示する。
export function LoginPage() {
  const { t } = useTranslation()
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setErrorMessage(null)
    try {
      await login(email, password)
      navigate('/', { replace: true })
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <PublicLayout>
      <AuthCard title={t('auth.loginTitle')}>
        {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
        <form onSubmit={onSubmit} style={formStyle}>
          <FormField label={t('auth.email')} required>
            <TextInput
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
              data-testid="login-email-input"
            />
          </FormField>
          <FormField label={t('auth.password')} required>
            <PasswordInput
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              data-testid="login-password-input"
            />
          </FormField>
          <Button
            type="submit"
            variant="primary"
            loading={submitting}
            data-testid="login-submit-button"
          >
            {t('auth.loginButton')}
          </Button>
        </form>
        <p>
          <Link to="/register">{t('auth.registerLink')}</Link>
        </p>
      </AuthCard>
    </PublicLayout>
  )
}
