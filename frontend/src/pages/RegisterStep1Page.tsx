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
import { Alert, AuthCard, Button, FormField, PublicLayout, TextInput } from '../design-system/components'
import { startRegistration } from '../api/registrations'
import { ApiError } from '../api/http'
import type { Language } from '../i18n'

const formStyle = { display: 'flex', flexDirection: 'column' as const, gap: 'var(--mm-space-3)' }

// frontend-components.md §2。BR-REG-04によりレスポンスは常に同一のため、
// 既存メールアドレスか否かで表示を分岐しない（送信後は一律「送信完了」表示）。
export function RegisterStep1Page() {
  const { t, i18n } = useTranslation()
  const [email, setEmail] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSubmitting(true)
    setErrorMessage(null)
    try {
      await startRegistration(email, i18n.language as Language)
      setSubmitted(true)
    } catch (error) {
      setErrorMessage(error instanceof ApiError ? error.message : t('state.error'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <PublicLayout>
      <AuthCard title={t('registration.step1Title')}>
        {submitted ? (
          <Alert tone="success">{t('registration.step1Sent')}</Alert>
        ) : (
          <>
            {errorMessage ? <Alert tone="danger">{errorMessage}</Alert> : null}
            <form onSubmit={onSubmit} style={formStyle}>
              <FormField label={t('auth.email')} required>
                <TextInput
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                  required
                  data-testid="register-step1-email-input"
                />
              </FormField>
              <Button
                type="submit"
                variant="primary"
                loading={submitting}
                data-testid="register-step1-submit-button"
              >
                {t('registration.step1Submit')}
              </Button>
            </form>
          </>
        )}
      </AuthCard>
    </PublicLayout>
  )
}
