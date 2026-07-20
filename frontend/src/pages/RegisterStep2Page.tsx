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
import { Link, useSearchParams } from 'react-router-dom'
import {
  Alert,
  AuthCard,
  Button,
  FormField,
  PasswordInput,
  PublicLayout,
  Select,
  TextInput,
} from '../design-system/components'
import { completeRegistration } from '../api/registrations'
import { ApiError } from '../api/http'
import type { Language } from '../design-system/i18n'

const formStyle = { display: 'flex', flexDirection: 'column' as const, gap: 'var(--mm-space-3)' }

// frontend-components.md §3。BR-REG-05（氏名・言語設定、Q9追加要望）。
export function RegisterStep2Page() {
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')

  const [fullName, setFullName] = useState('')
  const [preferredLanguage, setPreferredLanguage] = useState<Language>('ja')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [complete, setComplete] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  if (!token) {
    return (
      <PublicLayout>
        <AuthCard title={t('registration.step2Title')}>
          <Alert tone="danger">{t('registration.invalidLink')}</Alert>
        </AuthCard>
      </PublicLayout>
    )
  }

  if (complete) {
    return (
      <PublicLayout>
        <AuthCard title={t('registration.step2Title')}>
          <Alert tone="success">{t('registration.step2Complete')}</Alert>
          <p>
            <Link to="/login">{t('registration.backToLogin')}</Link>
          </p>
        </AuthCard>
      </PublicLayout>
    )
  }

  const passwordMismatch =
    passwordConfirm !== '' && password !== passwordConfirm
      ? t('registration.passwordMismatch')
      : undefined

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (password === '' || password !== passwordConfirm) {
      return
    }
    setSubmitting(true)
    setErrorMessage(null)
    try {
      await completeRegistration(token, fullName, preferredLanguage, password)
      setComplete(true)
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <PublicLayout>
      <AuthCard title={t('registration.step2Title')}>
        {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
        <form onSubmit={onSubmit} style={formStyle}>
          <FormField label={t('registration.fullName')} required>
            <TextInput
              value={fullName}
              onChange={(event) => setFullName(event.target.value)}
              required
              data-testid="register-step2-fullname-input"
            />
          </FormField>
          <FormField label={t('registration.language')} required>
            <Select
              value={preferredLanguage}
              onChange={(event) => setPreferredLanguage(event.target.value as Language)}
              data-testid="register-step2-language-select"
            >
              <option value="ja">日本語</option>
              <option value="en">English</option>
            </Select>
          </FormField>
          <FormField label={t('registration.password')} required>
            <PasswordInput
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              data-testid="register-step2-password-input"
            />
          </FormField>
          <FormField label={t('registration.passwordConfirm')} required error={passwordMismatch}>
            <PasswordInput
              value={passwordConfirm}
              onChange={(event) => setPasswordConfirm(event.target.value)}
              required
              data-testid="register-step2-password-confirm-input"
            />
          </FormField>
          <Button
            type="submit"
            variant="primary"
            loading={submitting}
            data-testid="register-step2-submit-button"
          >
            {t('registration.step2Submit')}
          </Button>
        </form>
      </AuthCard>
    </PublicLayout>
  )
}
