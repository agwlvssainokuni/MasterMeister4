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

import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { PasswordInput, Select, TextArea, TextInput } from './TextInput'

describe('TextInput', () => {
  it('invalidのときaria-invalidが設定される', () => {
    render(<TextInput invalid aria-label="メールアドレス" />)
    expect(screen.getByLabelText('メールアドレス')).toHaveAttribute('aria-invalid', 'true')
  })
})

describe('PasswordInput', () => {
  it('トグルで表示/非表示が切り替わる', async () => {
    render(<PasswordInput aria-label="パスワード" />)
    const input = screen.getByLabelText('パスワード')
    expect(input).toHaveAttribute('type', 'password')

    await userEvent.click(screen.getByRole('button', { name: 'パスワードを表示' }))
    expect(input).toHaveAttribute('type', 'text')

    await userEvent.click(screen.getByRole('button', { name: 'パスワードを非表示' }))
    expect(input).toHaveAttribute('type', 'password')
  })
})

describe('TextArea', () => {
  it('複数行テキストを入力できる', async () => {
    render(<TextArea aria-label="メモ" />)
    const textarea = screen.getByLabelText('メモ')
    await userEvent.type(textarea, '1行目\n2行目')
    expect(textarea).toHaveValue('1行目\n2行目')
  })
})

describe('Select', () => {
  it('選択肢を選択できる', async () => {
    render(
      <Select aria-label="役割" defaultValue="user">
        <option value="admin">管理者</option>
        <option value="user">一般ユーザ</option>
      </Select>,
    )
    const select = screen.getByLabelText('役割')
    await userEvent.selectOptions(select, '管理者')
    expect(select).toHaveValue('admin')
  })
})
