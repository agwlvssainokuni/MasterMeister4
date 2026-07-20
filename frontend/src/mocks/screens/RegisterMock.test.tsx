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
import { describe, expect, it } from 'vitest'
import { renderMock } from '../../test/render'
import { RegisterMock } from './RegisterMock'

describe('RegisterMock', () => {
  it('Step1: メールアドレス送信で送信完了状態になる', async () => {
    renderMock(<RegisterMock />)
    await userEvent.type(screen.getByTestId('register-mock-email-input'), 'user@example.com')
    await userEvent.click(screen.getByTestId('register-mock-submit-button'))
    expect(screen.getByText(/確認メールを送信しました/)).toBeInTheDocument()
  })

  it('Step2（デモ導線経由）: パスワード不一致でエラーを表示する', async () => {
    renderMock(<RegisterMock />)
    await userEvent.click(screen.getByTestId('register-mock-submit-button'))
    await userEvent.click(screen.getByRole('button', { name: '（デモ）確認リンクを開く' }))
    await userEvent.type(screen.getByTestId('register-mock-password-input'), 'password1')
    await userEvent.type(screen.getByTestId('register-mock-password-confirm-input'), 'password2')
    expect(screen.getByText('パスワードが一致しません')).toBeInTheDocument()
  })

  it('Step2: パスワード一致で登録完了状態になる', async () => {
    renderMock(<RegisterMock />)
    await userEvent.click(screen.getByTestId('register-mock-submit-button'))
    await userEvent.click(screen.getByRole('button', { name: '（デモ）確認リンクを開く' }))
    await userEvent.type(screen.getByTestId('register-mock-password-input'), 'password1')
    await userEvent.type(screen.getByTestId('register-mock-password-confirm-input'), 'password1')
    await userEvent.click(screen.getByTestId('register-mock-complete-button'))
    expect(screen.getByText(/登録が完了しました/)).toBeInTheDocument()
  })

  it('期限切れリンク（デモ導線経由）: エラー状態を表示する', async () => {
    renderMock(<RegisterMock />)
    await userEvent.click(screen.getByTestId('register-mock-submit-button'))
    await userEvent.click(screen.getByRole('button', { name: '（デモ）期限切れリンクを開く' }))
    expect(screen.getByText('リンクの有効期限が切れているか、無効なリンクです')).toBeInTheDocument()
  })
})
