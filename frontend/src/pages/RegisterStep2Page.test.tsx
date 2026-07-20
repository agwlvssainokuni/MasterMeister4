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
import { RegisterStep2Page } from './RegisterStep2Page'
import { renderPage } from '../test/render'

vi.mock('../api/registrations')

describe('RegisterStep2Page', () => {
  afterEach(() => {
    vi.resetAllMocks()
  })

  it('URLに?tokenが無い場合は無効なリンクと表示する', () => {
    renderPage(<RegisterStep2Page />, { initialEntries: ['/register/complete'] })
    expect(screen.getByText('無効なリンクです')).toBeInTheDocument()
    expect(screen.queryByTestId('register-step2-submit-button')).not.toBeInTheDocument()
  })

  it('パスワード確認欄が不一致の場合はエラーを表示する', async () => {
    renderPage(<RegisterStep2Page />, { initialEntries: ['/register/complete?token=abc123'] })
    await userEvent.type(screen.getByTestId('register-step2-password-input'), 'Passw0rd!')
    await userEvent.type(screen.getByTestId('register-step2-password-confirm-input'), 'different')
    expect(screen.getByText('パスワードが一致しません')).toBeInTheDocument()
  })

  it('入力成功時はトークンを使って登録完了APIを呼び出し、完了メッセージを表示する', async () => {
    vi.mocked(registrationsApi.completeRegistration).mockResolvedValueOnce(undefined)
    renderPage(<RegisterStep2Page />, { initialEntries: ['/register/complete?token=abc123'] })
    await userEvent.type(screen.getByTestId('register-step2-fullname-input'), '山田太郎')
    await userEvent.type(screen.getByTestId('register-step2-password-input'), 'Passw0rd!')
    await userEvent.type(screen.getByTestId('register-step2-password-confirm-input'), 'Passw0rd!')
    await userEvent.click(screen.getByTestId('register-step2-submit-button'))
    expect(
      await screen.findByText('登録が完了しました。管理者の承認をお待ちください。'),
    ).toBeInTheDocument()
    expect(registrationsApi.completeRegistration).toHaveBeenCalledWith(
      'abc123',
      '山田太郎',
      'ja',
      'Passw0rd!',
    )
  })

  it('サーバエラー時はエラーメッセージを表示する', async () => {
    vi.mocked(registrationsApi.completeRegistration).mockRejectedValueOnce(
      new ApiError('REGISTRATION_TOKEN_INVALID', 'リンクの有効期限が切れています', 400),
    )
    renderPage(<RegisterStep2Page />, { initialEntries: ['/register/complete?token=abc123'] })
    await userEvent.type(screen.getByTestId('register-step2-fullname-input'), '山田太郎')
    await userEvent.type(screen.getByTestId('register-step2-password-input'), 'Passw0rd!')
    await userEvent.type(screen.getByTestId('register-step2-password-confirm-input'), 'Passw0rd!')
    await userEvent.click(screen.getByTestId('register-step2-submit-button'))
    expect(await screen.findByText('リンクの有効期限が切れています')).toBeInTheDocument()
  })
})
