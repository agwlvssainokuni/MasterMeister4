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

import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderMock } from '../../test/render'
import { DashboardMock } from './DashboardMock'

describe('DashboardMock', () => {
  it('通常状態: 承認待ちユーザ一覧を表示する', () => {
    renderMock(<DashboardMock />)
    expect(screen.getByText('鈴木 一郎')).toBeInTheDocument()
    expect(screen.getByTestId('dashboard-mock-approve-2')).toBeInTheDocument()
  })

  it('空状態: 検索条件に合致しない場合はEmptyStateを表示する', async () => {
    renderMock(<DashboardMock />)
    await userEvent.type(screen.getByTestId('filter-bar-search-input'), '該当なしキーワード')
    expect(screen.getByTestId('empty-state')).toBeInTheDocument()
  })

  it('承認操作で確認ダイアログを表示し、確認するとトースト表示される', async () => {
    renderMock(<DashboardMock />)
    await userEvent.click(screen.getByTestId('dashboard-mock-approve-2'))
    const dialog = screen.getByRole('dialog')
    expect(dialog).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: '承認' }))
    expect(screen.getByRole('status')).toHaveTextContent('を承認しました')
  })
})
