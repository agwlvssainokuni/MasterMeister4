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

import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderMock } from '../../test/render'
import { PermissionsMock } from './PermissionsMock'

describe('PermissionsMock', () => {
  it('通常状態: 権限一覧を表示する', () => {
    renderMock(<PermissionsMock />)
    expect(screen.getByText('営業部グループ')).toBeInTheDocument()
  })

  it('編集して保存するとトーストが表示される', async () => {
    renderMock(<PermissionsMock />)
    await userEvent.click(screen.getByTestId('permissions-mock-edit-2'))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    await userEvent.click(screen.getByTestId('permissions-mock-save-button'))
    expect(screen.getByRole('status')).toHaveTextContent('権限設定を保存しました')
  })
})
