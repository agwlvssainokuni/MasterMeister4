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

import { act, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderMock } from '../../test/render'
import { LoginMock } from './LoginMock'

describe('LoginMock', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('通常状態でフォームを表示する', () => {
    renderMock(<LoginMock />)
    expect(screen.getByTestId('login-mock-email-input')).toBeInTheDocument()
    expect(screen.getByTestId('login-mock-password-input')).toBeInTheDocument()
  })

  it('不正な形式のメールアドレスでエラーを表示する', async () => {
    renderMock(<LoginMock />)
    await userEvent.type(screen.getByTestId('login-mock-email-input'), 'invalid')
    expect(screen.getByRole('alert')).toHaveTextContent('メールアドレスの形式が正しくありません')
  })

  it('送信後、一定時間でエラー状態（ログイン失敗）を表示する', async () => {
    renderMock(<LoginMock />)
    await userEvent.click(screen.getByTestId('login-mock-submit-button'))
    act(() => {
      vi.advanceTimersByTime(1000)
    })
    expect(screen.getByText('メールアドレスまたはパスワードが正しくありません')).toBeInTheDocument()
  })
})
