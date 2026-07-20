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

import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Checkbox, RadioGroup, Switch } from './Choice'

describe('Checkbox', () => {
  it('クリックでチェック状態が切り替わる', async () => {
    const onChange = vi.fn()
    render(<Checkbox label="同意する" onChange={onChange} />)
    await userEvent.click(screen.getByRole('checkbox', { name: '同意する' }))
    expect(onChange).toHaveBeenCalledTimes(1)
  })
})

describe('RadioGroup', () => {
  it('選択を変更するとonChangeに値が渡る', async () => {
    const onChange = vi.fn()
    render(
      <RadioGroup
        name="role"
        value="user"
        onChange={onChange}
        options={[
          { value: 'admin', label: '管理者' },
          { value: 'user', label: '一般ユーザ' },
        ]}
      />,
    )
    await userEvent.click(screen.getByRole('radio', { name: '管理者' }))
    expect(onChange).toHaveBeenCalledWith('admin')
  })
})

describe('Switch', () => {
  it('クリックでonChangeにtrue/falseが渡る', () => {
    // input要素はpointer-events: noneで視覚的に隠しているため、
    // ラベル経由のネイティブクリック委譲をfireEventで直接検証する。
    const onChange = vi.fn()
    render(<Switch checked={false} onChange={onChange} label="有効化" />)
    fireEvent.click(screen.getByRole('switch', { name: '有効化' }))
    expect(onChange).toHaveBeenCalledWith(true)
  })
})
