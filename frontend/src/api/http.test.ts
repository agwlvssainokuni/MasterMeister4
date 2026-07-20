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

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, UNAUTHORIZED_EVENT, apiFetch } from './http'
import { clearTokens, getAccessToken, setTokens } from '../auth/tokenStorage'

function jsonResponse(status: number, body: unknown) {
  return {
    status,
    ok: status >= 200 && status < 300,
    statusText: 'error',
    json: () => Promise.resolve(body),
  } as Response
}

describe('apiFetch', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    clearTokens()
  })

  it('正常応答のJSONをそのまま返す', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(200, { value: 1 }))
    await expect(apiFetch<{ value: number }>('/api/x')).resolves.toEqual({ value: 1 })
  })

  it('204応答はundefinedを返す', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({ status: 204, ok: true } as Response)
    await expect(apiFetch('/api/x')).resolves.toBeUndefined()
  })

  it('エラー応答はcode/messageを持つApiErrorを投げる', async () => {
    vi.mocked(fetch).mockResolvedValueOnce(
      jsonResponse(400, { code: 'VALIDATION_ERROR', message: '入力値が不正です' }),
    )
    await expect(apiFetch('/api/x')).rejects.toMatchObject({
      code: 'VALIDATION_ERROR',
      message: '入力値が不正です',
      status: 400,
    })
  })

  it('auth:trueの場合、Authorizationヘッダーにアクセストークンを付与する', async () => {
    setTokens('access-1', 'refresh-1')
    vi.mocked(fetch).mockResolvedValueOnce(jsonResponse(200, { value: 1 }))
    await apiFetch('/api/x', { auth: true })
    const headers = vi.mocked(fetch).mock.calls[0][1]?.headers as Record<string, string>
    expect(headers.Authorization).toBe('Bearer access-1')
  })

  it('auth:trueで401を受けた場合、リフレッシュ後に1度だけ再試行して成功する', async () => {
    setTokens('expired-access', 'refresh-1')
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse(401, { code: 'AUTH_TOKEN_EXPIRED', message: '期限切れ' }))
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: 'new-access', refreshToken: 'new-refresh' }))
      .mockResolvedValueOnce(jsonResponse(200, { value: 1 }))

    await expect(apiFetch<{ value: number }>('/api/x', { auth: true })).resolves.toEqual({
      value: 1,
    })
    expect(fetch).toHaveBeenCalledTimes(3)
    expect(getAccessToken()).toBe('new-access')
  })

  it('リフレッシュも失敗した場合、トークンをクリアしUNAUTHORIZED_EVENTを発行して元のエラーを投げる', async () => {
    setTokens('expired-access', 'expired-refresh')
    vi.mocked(fetch)
      .mockResolvedValueOnce(jsonResponse(401, { code: 'AUTH_TOKEN_EXPIRED', message: '期限切れ' }))
      .mockResolvedValueOnce(jsonResponse(401, { code: 'AUTH_TOKEN_EXPIRED', message: '期限切れ' }))

    const onUnauthorized = vi.fn()
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
    try {
      await expect(apiFetch('/api/x', { auth: true })).rejects.toBeInstanceOf(ApiError)
      expect(getAccessToken()).toBeNull()
      expect(onUnauthorized).toHaveBeenCalledTimes(1)
    } finally {
      window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
    }
  })
})
