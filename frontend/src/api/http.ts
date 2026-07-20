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

import i18n from '../design-system/i18n'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '../auth/tokenStorage'

// business-rules.md BR-API-01。統一エラーレスポンス形式 { code, message }。
export class ApiError extends Error {
  readonly code: string
  readonly status: number

  constructor(code: string, message: string, status: number) {
    super(message)
    this.code = code
    this.status = status
  }
}

interface ApiFetchOptions {
  method?: string
  body?: unknown
  /** trueの場合、Authorizationヘッダーを付与し、401時に一度だけリフレッシュ再試行する */
  auth?: boolean
}

// 未ログイン状態への遷移をAuthContextへ通知するためのカスタムイベント（http.tsとAuthContextの
// 循環依存を避けるため、DOMイベント経由で疎結合にする）。
export const UNAUTHORIZED_EVENT = 'mastermeister:unauthorized'

async function rawFetch<T>(path: string, options: ApiFetchOptions, accessToken: string | null): Promise<T> {
  const headers: Record<string, string> = {
    // frontendのLanguageSwitcherで選択中の言語をサーバに伝え、BR-API-01のエラーメッセージ・
    // メール文面等をこの言語で生成させる（NFR-7.3）。
    'Accept-Language': i18n.language,
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (options.auth && accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }

  const response = await fetch(path, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })

  if (response.status === 204) {
    return undefined as T
  }

  const data: unknown = await response.json().catch(() => null)

  if (!response.ok) {
    const errorBody = data as { code?: string; message?: string } | null
    throw new ApiError(
      errorBody?.code ?? 'UNKNOWN_ERROR',
      errorBody?.message ?? response.statusText,
      response.status,
    )
  }

  return data as T
}

async function tryRefreshAccessToken(): Promise<string | null> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    return null
  }
  try {
    const tokens = await rawFetch<{ accessToken: string; refreshToken: string }>(
      '/api/auth/refresh',
      { method: 'POST', body: { refreshToken } },
      null,
    )
    setTokens(tokens.accessToken, tokens.refreshToken)
    return tokens.accessToken
  } catch {
    return null
  }
}

export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  if (!options.auth) {
    return rawFetch<T>(path, options, null)
  }

  try {
    return await rawFetch<T>(path, options, getAccessToken())
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      const newAccessToken = await tryRefreshAccessToken()
      if (newAccessToken) {
        return rawFetch<T>(path, options, newAccessToken)
      }
      clearTokens()
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
    }
    throw error
  }
}
