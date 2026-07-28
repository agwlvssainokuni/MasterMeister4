import type { APIRequestContext } from '@playwright/test'
import { extractTokenFromMailText, waitForLatestMailText } from './mailpit'

/**
 * UI操作を介さず、APIを直接呼び出してユーザ登録〜承認〜ログインまでを完了させる。
 * 一般ユーザのアクセス拒否確認等、UI操作の対象ではないテストのセットアップに使う。
 */
export async function registerAndApproveUser(
  request: APIRequestContext,
  baseURL: string,
  adminAccessToken: string,
  email: string,
  password: string,
  fullName: string,
): Promise<void> {
  await request.post(`${baseURL}/api/registrations`, {
    data: { email, language: 'ja' },
  })

  const mailText = await waitForLatestMailText(request, email)
  const token = extractTokenFromMailText(mailText)

  await request.post(`${baseURL}/api/registrations/${token}/complete`, {
    data: { fullName, preferredLanguage: 'ja', password },
  })

  const usersResponse = await request.get(`${baseURL}/api/admin/users?status=PENDING`, {
    headers: { Authorization: `Bearer ${adminAccessToken}` },
  })
  const users = (await usersResponse.json()) as Array<{ id: number; email: string }>
  const target = users.find((u) => u.email === email)
  if (!target) {
    throw new Error(`Registered user ${email} not found in PENDING list`)
  }

  await request.post(`${baseURL}/api/admin/users/${target.id}/approve`, {
    headers: { Authorization: `Bearer ${adminAccessToken}` },
  })
}

export async function login(
  request: APIRequestContext,
  baseURL: string,
  email: string,
  password: string,
): Promise<string> {
  const response = await request.post(`${baseURL}/api/auth/login`, {
    data: { email, password },
  })
  const body = (await response.json()) as { accessToken: string }
  return body.accessToken
}
