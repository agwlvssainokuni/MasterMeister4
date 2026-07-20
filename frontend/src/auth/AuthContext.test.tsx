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
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider, useAuth } from './AuthContext'
import { UNAUTHORIZED_EVENT } from '../api/http'
import { clearTokens, getAccessToken, setTokens } from './tokenStorage'
import * as authApi from '../api/auth'

vi.mock('../api/auth')

function Probe() {
  const { isAuthenticated, login, logout } = useAuth()
  return (
    <div>
      <span data-testid="probe-status">{isAuthenticated ? 'in' : 'out'}</span>
      <button onClick={() => void login('user@example.com', 'password')}>login</button>
      <button onClick={() => void logout()}>logout</button>
    </div>
  )
}

describe('AuthContext', () => {
  afterEach(() => {
    clearTokens()
    vi.resetAllMocks()
  })

  it('Provider外でuseAuthを呼ぶとエラーになる', () => {
    const Broken = () => {
      useAuth()
      return null
    }
    expect(() => render(<Broken />)).toThrowError('useAuth must be used within AuthProvider')
  })

  it('起動時、アクセストークンが無ければ未認証、あれば認証済みとして初期化される', () => {
    const { unmount } = render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    expect(screen.getByTestId('probe-status')).toHaveTextContent('out')
    unmount()

    setTokens('access-1', 'refresh-1')
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    expect(screen.getByTestId('probe-status')).toHaveTextContent('in')
  })

  it('loginでトークンを保存し認証済みになる', async () => {
    vi.mocked(authApi.login).mockResolvedValueOnce({
      accessToken: 'access-2',
      refreshToken: 'refresh-2',
    })
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'login' }))
    await waitFor(() => expect(screen.getByTestId('probe-status')).toHaveTextContent('in'))
    expect(getAccessToken()).toBe('access-2')
  })

  it('logoutでサーバ失効APIを呼びトークンをクリアする', async () => {
    setTokens('access-1', 'refresh-1')
    vi.mocked(authApi.logout).mockResolvedValueOnce(undefined)
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'logout' }))
    await waitFor(() => expect(screen.getByTestId('probe-status')).toHaveTextContent('out'))
    expect(authApi.logout).toHaveBeenCalledWith('refresh-1')
    expect(getAccessToken()).toBeNull()
  })

  it('logoutのサーバ呼び出しが失敗してもクライアント側のトークンはクリアされる', async () => {
    setTokens('access-1', 'refresh-1')
    vi.mocked(authApi.logout).mockRejectedValueOnce(new Error('network error'))
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'logout' }))
    await waitFor(() => expect(screen.getByTestId('probe-status')).toHaveTextContent('out'))
    expect(getAccessToken()).toBeNull()
  })

  it('UNAUTHORIZED_EVENTを受けると未認証状態に遷移する', async () => {
    setTokens('access-1', 'refresh-1')
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    expect(screen.getByTestId('probe-status')).toHaveTextContent('in')
    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    await waitFor(() => expect(screen.getByTestId('probe-status')).toHaveTextContent('out'))
  })
})
