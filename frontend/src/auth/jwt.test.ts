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

import { describe, expect, it } from 'vitest'
import { decodeJwtEmail } from './jwt'

function makeToken(payload: unknown): string {
  const header = window.btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body = window.btoa(JSON.stringify(payload))
  return `${header}.${body}.signature`
}

describe('decodeJwtEmail', () => {
  it('emailクレームを取り出す', () => {
    expect(decodeJwtEmail(makeToken({ sub: '1', email: 'user@example.com' }))).toBe(
      'user@example.com',
    )
  })

  it('emailクレームが無い場合はnullを返す', () => {
    expect(decodeJwtEmail(makeToken({ sub: '1' }))).toBeNull()
  })

  it('ペイロード部が無い不正なトークンはnullを返す', () => {
    expect(decodeJwtEmail('not-a-jwt')).toBeNull()
  })

  it('ペイロードがJSONとしてパースできない場合はnullを返す', () => {
    expect(decodeJwtEmail(`header.${window.btoa('not-json')}.signature`)).toBeNull()
  })
})
