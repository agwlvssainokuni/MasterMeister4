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
import { describe, expect, it, vi } from 'vitest'
import { FilterBar } from './FilterBar'

describe('FilterBar', () => {
  it('検索欄への入力でonSearchChangeが呼ばれる', async () => {
    const onSearchChange = vi.fn()
    render(<FilterBar searchValue="" onSearchChange={onSearchChange} />)
    await userEvent.type(screen.getByTestId('filter-bar-search-input'), 'a')
    expect(onSearchChange).toHaveBeenCalledWith('a')
  })

  it('childrenを追加コントロールとして表示する', () => {
    render(
      <FilterBar>
        <button>絞り込み</button>
      </FilterBar>,
    )
    expect(screen.getByRole('button', { name: '絞り込み' })).toBeInTheDocument()
  })
})
