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
import { MasterDataMock } from './MasterDataMock'

describe('MasterDataMock', () => {
  it('通常状態: 顧客マスタのレコード一覧を表示する', () => {
    renderMock(<MasterDataMock />)
    expect(screen.getByDisplayValue('株式会社アカツキ商事')).toBeInTheDocument()
  })

  it('行追加で変更バーが表示され、反映すると確認ダイアログが開く', async () => {
    renderMock(<MasterDataMock />)
    await userEvent.click(screen.getByRole('button', { name: /行を追加/ }))
    expect(screen.getByText(/追加 1件/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '保存' }))
    const dialog = screen.getByRole('dialog')
    expect(
      within(dialog).getByText('変更内容をまとめて反映します。よろしいですか？'),
    ).toBeInTheDocument()
  })
})
