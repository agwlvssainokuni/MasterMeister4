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
