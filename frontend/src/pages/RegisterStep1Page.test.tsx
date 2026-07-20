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

import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/http'
import * as registrationsApi from '../api/registrations'
import { RegisterStep1Page } from './RegisterStep1Page'
import { renderPage } from '../test/render'

vi.mock('../api/registrations')

describe('RegisterStep1Page', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('送信成功時は同一の「送信完了」メッセージを表示する（BR-REG-04: 既存有無で表示を分岐しない）', async () => {
    vi.mocked(registrationsApi.startRegistration).mockResolvedValueOnce(undefined)
    renderPage(<RegisterStep1Page />)
    await userEvent.type(screen.getByTestId('register-step1-email-input'), 'new@example.com')
    await userEvent.click(screen.getByTestId('register-step1-submit-button'))
    expect(
      await screen.findByText(
        '確認メールを送信しました。メール内のリンクからパスワードを設定してください。',
      ),
    ).toBeInTheDocument()
    expect(registrationsApi.startRegistration).toHaveBeenCalledWith('new@example.com', 'ja')
  })

  it('サーバエラー時はエラーメッセージを表示する', async () => {
    vi.mocked(registrationsApi.startRegistration).mockRejectedValueOnce(
      new ApiError('REGISTRATION_RATE_LIMITED', 'しばらく時間をおいてから再度お試しください', 429),
    )
    renderPage(<RegisterStep1Page />)
    await userEvent.type(screen.getByTestId('register-step1-email-input'), 'new@example.com')
    await userEvent.click(screen.getByTestId('register-step1-submit-button'))
    expect(
      await screen.findByText('しばらく時間をおいてから再度お試しください'),
    ).toBeInTheDocument()
  })
})
