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

import { Checkbox } from './Checkbox'

describe('Checkbox', () => {
  it('is labelled and toggleable by clicking the label', async () => {
    const user = userEvent.setup()
    render(<Checkbox label="Remember me" />)

    const checkbox = screen.getByRole('checkbox', { name: 'Remember me' })
    expect(checkbox).not.toBeChecked()

    await user.click(screen.getByText('Remember me'))

    expect(checkbox).toBeChecked()
  })

  it('is toggleable via keyboard', async () => {
    const user = userEvent.setup()
    render(<Checkbox label="Remember me" />)

    await user.tab()
    const checkbox = screen.getByRole('checkbox', { name: 'Remember me' })
    expect(checkbox).toHaveFocus()

    await user.keyboard(' ')
    expect(checkbox).toBeChecked()
  })

  it('applies the testId as data-testid', () => {
    render(<Checkbox label="Remember me" testId="remember-me-checkbox" />)
    expect(screen.getByTestId('remember-me-checkbox')).toBeInTheDocument()
  })
})
