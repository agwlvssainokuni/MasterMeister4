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

import { Switch } from './Switch'

describe('Switch', () => {
  it('exposes a switch role reflecting the checked state', () => {
    render(<Switch checked={false} onChange={() => {}} label="通知を受け取る" />)
    expect(screen.getByRole('switch', { name: '通知を受け取る' })).not.toBeChecked()
  })

  it('calls onChange with the new state when toggled', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(<Switch checked={false} onChange={onChange} label="通知を受け取る" />)

    await user.click(screen.getByRole('switch'))

    expect(onChange).toHaveBeenCalledWith(true)
  })

  it('applies the testId as data-testid', () => {
    render(<Switch checked={false} onChange={() => {}} testId="notifications-switch" />)
    expect(screen.getByTestId('notifications-switch')).toBeInTheDocument()
  })
})
