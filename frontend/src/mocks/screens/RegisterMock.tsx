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
import {
  Alert,
  AuthCard,
  Button,
  FormField,
  PasswordInput,
  PublicLayout,
  TextInput,
} from '../../design-system/components'

type Step = 'email' | 'sent' | 'password' | 'tokenError' | 'complete'

const formStyle = { display: 'flex', flexDirection: 'column' as const, gap: 'var(--mm-space-3)' }

// デザイン確認用の静的モック。2段階登録フロー（FR-1.1〜1.3）のうち、
// メールアドレス送信（Step1）とパスワード設定（Step2、メール内リンクから遷移）を
// 1画面内でデモ切替できるようにしている。データ連携・業務ロジックは伴わない（FR-0.5）。
export function RegisterMock() {
  const { t } = useTranslation('design-system')
  const { t: tc } = useTranslation()
  const [step, setStep] = useState<Step>('email')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')

  const onSubmitEmail = (event: FormEvent) => {
    event.preventDefault()
    setStep('sent')
  }

  const passwordMismatch =
    passwordConfirm !== '' && password !== passwordConfirm
      ? t('mock.register.passwordMismatch')
      : undefined

  const onSubmitPassword = (event: FormEvent) => {
    event.preventDefault()
    if (password === '' || password !== passwordConfirm) {
      return
    }
    setStep('complete')
  }

  if (step === 'password' || step === 'tokenError') {
    return (
      <PublicLayout>
        <AuthCard title={t('mock.register.step2Title')}>
          {step === 'tokenError' ? (
            <Alert tone="danger">{t('mock.register.tokenError')}</Alert>
          ) : (
            <form onSubmit={onSubmitPassword} style={formStyle}>
              <FormField label={t('mock.register.password')} required>
                <PasswordInput
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  data-testid="register-mock-password-input"
                />
              </FormField>
              <FormField
                label={t('mock.register.passwordConfirm')}
                required
                error={passwordMismatch}
              >
                <PasswordInput
                  value={passwordConfirm}
                  onChange={(event) => setPasswordConfirm(event.target.value)}
                  data-testid="register-mock-password-confirm-input"
                />
              </FormField>
              <Button type="submit" variant="primary" data-testid="register-mock-complete-button">
                {tc('action.confirm')}
              </Button>
            </form>
          )}
        </AuthCard>
      </PublicLayout>
    )
  }

  if (step === 'complete') {
    return (
      <PublicLayout>
        <AuthCard title={t('mock.register.step2Title')}>
          <Alert tone="success">{t('mock.register.complete')}</Alert>
        </AuthCard>
      </PublicLayout>
    )
  }

  return (
    <PublicLayout>
      <AuthCard title={t('mock.register.step1Title')}>
        {step === 'sent' ? (
          <>
            <Alert tone="success">{t('mock.register.sent')}</Alert>
            {/* モック内デモ導線: 実際にはメール内リンクから遷移する */}
            <Button variant="ghost" size="sm" onClick={() => setStep('password')}>
              （デモ）確認リンクを開く
            </Button>
            <Button variant="ghost" size="sm" onClick={() => setStep('tokenError')}>
              （デモ）期限切れリンクを開く
            </Button>
          </>
        ) : (
          <form onSubmit={onSubmitEmail} style={formStyle}>
            <FormField label={t('mock.register.email')} required>
              <TextInput
                type="email"
                autoComplete="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                data-testid="register-mock-email-input"
              />
            </FormField>
            <Button type="submit" variant="primary" data-testid="register-mock-submit-button">
              {tc('action.confirm')}
            </Button>
          </form>
        )}
      </AuthCard>
    </PublicLayout>
  )
}
