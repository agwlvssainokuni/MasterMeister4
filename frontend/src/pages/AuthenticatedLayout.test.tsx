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
import { AuthenticatedLayout } from './AuthenticatedLayout'
import { clearTokens, setTokens } from '../auth/tokenStorage'
import * as authApi from '../api/auth'

vi.mock('../api/auth')

function makeAccessToken(email: string): string {
  const header = window.btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body = window.btoa(JSON.stringify({ sub: '1', email }))
  return `${header}.${body}.signature`
}

function renderLayout() {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={['/']}>
        <AuthProvider>
          <Routes>
            <Route
              path="/"
              element={
                <AuthenticatedLayout>
                  <p>コンテンツ</p>
                </AuthenticatedLayout>
              }
            />
            <Route path="/login" element={<p>ログイン画面</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

describe('AuthenticatedLayout', () => {
  afterEach(() => {
    clearTokens()
    vi.resetAllMocks()
  })

  it('アクセストークンのemailクレームをユーザ表示名として表示する', () => {
    setTokens(makeAccessToken('user@example.com'), 'refresh-1')
    renderLayout()
    expect(screen.getByText('user@example.com')).toBeInTheDocument()
    expect(screen.getByText('コンテンツ')).toBeInTheDocument()
  })

  it('ログアウトボタン押下でログアウトし、/loginへ遷移する', async () => {
    setTokens(makeAccessToken('user@example.com'), 'refresh-1')
    vi.mocked(authApi.logout).mockResolvedValueOnce(undefined)
    renderLayout()
    await userEvent.click(screen.getByTestId('app-shell-logout-button'))
    await waitFor(() => expect(screen.getByText('ログイン画面')).toBeInTheDocument())
  })
})
