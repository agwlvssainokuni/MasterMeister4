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

import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { ThemeProvider } from '../design-system/theme/ThemeProvider'
import { AuthProvider } from '../auth/AuthContext'
import { HomePage } from './HomePage'

function renderHomePage() {
  return render(
    <ThemeProvider>
      <MemoryRouter initialEntries={['/']}>
        <AuthProvider>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/users" element={<p>ユーザ管理画面</p>} />
            <Route path="/connections" element={<p>RDBMS接続設定画面</p>} />
            <Route path="/master-data" element={<p>マスタメンテナンス画面</p>} />
            <Route path="/saved-queries" element={<p>保存クエリ画面</p>} />
            <Route path="/query-execution" element={<p>クエリ実行画面</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </ThemeProvider>,
  )
}

describe('HomePage', () => {
  it('SideNavの9項目に対応するカードを表示し、実装済みは「ユーザ管理」「RDBMS接続設定」「グループ管理」「マスタメンテナンス」「保存クエリ」「クエリ実行」とする', () => {
    renderHomePage()
    // NAV_ROUTESの全項目のタイトルが表示される（SideNav側にも同名の項目があるため、
    // カード側は実装済みカードのdata-testidで、非活性カードはgetAllByTextで確認する）
    expect(within(screen.getByTestId('feature-card-users')).getByText('ユーザ管理')).toBeInTheDocument()
    expect(
      within(screen.getByTestId('feature-card-connections')).getByText('RDBMS接続設定'),
    ).toBeInTheDocument()
    expect(
      within(screen.getByTestId('feature-card-master-data')).getByText('マスタメンテナンス'),
    ).toBeInTheDocument()
    expect(
      within(screen.getByTestId('feature-card-saved-queries')).getByText('保存クエリ'),
    ).toBeInTheDocument()
    expect(
      within(screen.getByTestId('feature-card-query-execution')).getByText('クエリ実行'),
    ).toBeInTheDocument()
    expect(screen.getAllByText('監査ログ').length).toBeGreaterThan(0)

    // 未実装カードは「準備中」バッジを持つ（UNIT-06でsavedQueries/queryExecutionが実装済みになった分、4→3に変化）
    expect(screen.getAllByText('準備中')).toHaveLength(3)

    // 実装済みカードのみクリック可能なボタンとして描画される
    expect(screen.getByTestId('feature-card-users')).toBeInTheDocument()
    expect(screen.getByTestId('feature-card-connections')).toBeInTheDocument()
    expect(screen.getByTestId('feature-card-master-data')).toBeInTheDocument()
    expect(screen.getByTestId('feature-card-saved-queries')).toBeInTheDocument()
    expect(screen.getByTestId('feature-card-query-execution')).toBeInTheDocument()
  })

  it('実装済みカードをクリックすると対応するページへ遷移する', async () => {
    renderHomePage()
    await userEvent.click(screen.getByTestId('feature-card-users'))
    expect(await screen.findByText('ユーザ管理画面')).toBeInTheDocument()
  })

  it('RDBMS接続設定カードをクリックすると対応するページへ遷移する', async () => {
    renderHomePage()
    await userEvent.click(screen.getByTestId('feature-card-connections'))
    expect(await screen.findByText('RDBMS接続設定画面')).toBeInTheDocument()
  })

  it('マスタメンテナンスカードをクリックすると対応するページへ遷移する', async () => {
    renderHomePage()
    await userEvent.click(screen.getByTestId('feature-card-master-data'))
    expect(await screen.findByText('マスタメンテナンス画面')).toBeInTheDocument()
  })

  it('保存クエリカードをクリックすると対応するページへ遷移する', async () => {
    renderHomePage()
    await userEvent.click(screen.getByTestId('feature-card-saved-queries'))
    expect(await screen.findByText('保存クエリ画面')).toBeInTheDocument()
  })

  it('クエリ実行カードをクリックすると対応するページへ遷移する', async () => {
    renderHomePage()
    await userEvent.click(screen.getByTestId('feature-card-query-execution'))
    expect(await screen.findByText('クエリ実行画面')).toBeInTheDocument()
  })
})
