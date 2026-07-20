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

import { PasswordInput } from './PasswordInput'

describe('PasswordInput', () => {
  it('masks input by default and reveals it when the visibility toggle is clicked', async () => {
    const user = userEvent.setup()
    render(<PasswordInput testId="password-input" />)

    const input = screen.getByTestId('password-input')
    expect(input).toHaveAttribute('type', 'password')

    await user.click(screen.getByRole('button', { name: 'パスワードを表示' }))

    expect(input).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'パスワードを隠す' })).toBeInTheDocument()
  })
})
