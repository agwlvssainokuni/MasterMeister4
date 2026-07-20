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

import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ThemeProvider } from '../design-system/theme/ThemeProvider'
import { AuthProvider } from '../auth/AuthContext'
import { ApiError } from '../api/http'
import * as authApi from '../api/auth'
import { LoginPage } from './LoginPage'
import { clearTokens } from '../auth/tokenStorage'

vi.mock('../api/auth')

function renderLoginPage() {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<p>トップ画面</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

describe('LoginPage', () => {
  afterEach(() => {
    clearTokens()
    vi.resetAllMocks()
  })

  it('メールアドレス・パスワード入力欄と登録リンクを表示する', () => {
    renderLoginPage()
    expect(screen.getByTestId('login-email-input')).toBeInTheDocument()
    expect(screen.getByTestId('login-password-input')).toBeInTheDocument()
    expect(screen.getByText('アカウントをお持ちでない方はこちら')).toBeInTheDocument()
  })

  it('ログイン成功でトップ画面へ遷移する', async () => {
    vi.mocked(authApi.login).mockResolvedValueOnce({
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
    })
    renderLoginPage()
    await userEvent.type(screen.getByTestId('login-email-input'), 'user@example.com')
    await userEvent.type(screen.getByTestId('login-password-input'), 'password')
    await userEvent.click(screen.getByTestId('login-submit-button'))
    await waitFor(() => expect(screen.getByText('トップ画面')).toBeInTheDocument())
  })

  it('ログイン失敗時はサーバから返されたエラーメッセージを表示する', async () => {
    vi.mocked(authApi.login).mockRejectedValueOnce(
      new ApiError('AUTH_INVALID_CREDENTIALS', 'メールアドレスまたはパスワードが正しくありません', 401),
    )
    renderLoginPage()
    await userEvent.type(screen.getByTestId('login-email-input'), 'user@example.com')
    await userEvent.type(screen.getByTestId('login-password-input'), 'wrong-password')
    await userEvent.click(screen.getByTestId('login-submit-button'))
    expect(
      await screen.findByText('メールアドレスまたはパスワードが正しくありません'),
    ).toBeInTheDocument()
  })
})
