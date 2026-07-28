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

import type { APIRequestContext } from '@playwright/test'

const MAILPIT_BASE_URL = 'http://localhost:8025'

/**
 * devenvのMailpit（メール送信確認用SMTPサーバ）から、指定した宛先に届いた
 * 最新メールの本文（テキスト形式）を取得する。ポーリングして到着を待つ。
 */
export async function waitForLatestMailText(
  request: APIRequestContext,
  toAddress: string,
  timeoutMs = 15_000,
): Promise<string> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const searchResponse = await request.get(
      `${MAILPIT_BASE_URL}/api/v1/search?query=${encodeURIComponent(`to:${toAddress}`)}`,
    )
    if (searchResponse.ok()) {
      const body = await searchResponse.json()
      const messages = body.messages as Array<{ ID: string; Created: string }>
      if (messages.length > 0) {
        const latest = messages.reduce((a, b) => (a.Created > b.Created ? a : b))
        const messageResponse = await request.get(`${MAILPIT_BASE_URL}/api/v1/message/${latest.ID}`)
        const message = await messageResponse.json()
        return message.Text as string
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500))
  }
  throw new Error(`Mail to ${toAddress} did not arrive within ${timeoutMs}ms`)
}

/**
 * メール本文から登録完了リンクのtokenクエリパラメータを抽出する。
 */
export function extractTokenFromMailText(text: string): string {
  const match = text.match(/[?&]token=([^\s&]+)/)
  if (!match) {
    throw new Error(`Could not find token in mail text: ${text}`)
  }
  return match[1]
}
