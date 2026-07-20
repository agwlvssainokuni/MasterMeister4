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

import { afterEach, describe, expect, it } from 'vitest'
import { clearTokens, getAccessToken, getRefreshToken, setTokens } from './tokenStorage'

describe('tokenStorage', () => {
  afterEach(() => {
    clearTokens()
  })

  it('未設定の場合はnullを返す', () => {
    expect(getAccessToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
  })

  it('setTokensで設定した値をsessionStorage経由で取得できる', () => {
    setTokens('access-1', 'refresh-1')
    expect(getAccessToken()).toBe('access-1')
    expect(getRefreshToken()).toBe('refresh-1')
    expect(window.sessionStorage.getItem('mastermeister.accessToken')).toBe('access-1')
    expect(window.sessionStorage.getItem('mastermeister.refreshToken')).toBe('refresh-1')
  })

  it('clearTokensで両方のトークンが削除される', () => {
    setTokens('access-1', 'refresh-1')
    clearTokens()
    expect(getAccessToken()).toBeNull()
    expect(getRefreshToken()).toBeNull()
  })
})
