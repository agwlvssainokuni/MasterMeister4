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
import { Pagination } from './Pagination'

describe('Pagination', () => {
  it('先頭ページでは前へボタンが無効、次へでonChangeが呼ばれる', async () => {
    const onChange = vi.fn()
    render(<Pagination page={1} totalPages={3} onChange={onChange} />)
    expect(screen.getByTestId('pagination-prev-button')).toBeDisabled()
    await userEvent.click(screen.getByTestId('pagination-next-button'))
    expect(onChange).toHaveBeenCalledWith(2)
  })

  it('最終ページでは次へボタンが無効になる', () => {
    render(<Pagination page={3} totalPages={3} onChange={() => {}} />)
    expect(screen.getByTestId('pagination-next-button')).toBeDisabled()
  })
})
