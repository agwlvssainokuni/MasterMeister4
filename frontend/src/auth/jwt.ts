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

// アクセストークン（JWT）のemailクレームを表示用に取り出す。署名検証は行わない
// （検証はサーバ側で行われており、ここでは表示目的のデコードのみ）。
export function decodeJwtEmail(accessToken: string): string | null {
  try {
    const payload = accessToken.split('.')[1]
    if (!payload) {
      return null
    }
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const json = window.atob(base64)
    const claims = JSON.parse(json) as { email?: string }
    return claims.email ?? null
  } catch {
    return null
  }
}

// UNIT-08 frontend-components.md「管理者判定」。AuthenticationServiceがJWT発行時に
// roleクレームへuser.getRole().name()を格納済みのため、decodeJwtEmailと同様の方式で
// 表示制御用に取り出す（実行者スコープSelectの表示・非表示のみに使用、権限境界としては
// 機能しない。実際のアクセス制御はサーバ側BR-QUERYHISTORY-03のフェイルクローズで担保する）。
export function decodeJwtRole(accessToken: string): string | null {
  try {
    const payload = accessToken.split('.')[1]
    if (!payload) {
      return null
    }
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const json = window.atob(base64)
    const claims = JSON.parse(json) as { role?: string }
    return claims.role ?? null
  } catch {
    return null
  }
}
