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

import { FormField } from '../FormField/FormField'
import { TextField } from './TextField'

describe('TextField', () => {
  it('accepts typed input', async () => {
    const user = userEvent.setup()
    render(<FormField label="Email">{(fieldProps) => <TextField {...fieldProps} />}</FormField>)

    const input = screen.getByLabelText('Email')
    await user.type(input, 'user@example.com')

    expect(input).toHaveValue('user@example.com')
  })

  it('applies the testId as data-testid', () => {
    render(<TextField testId="email-input" />)
    expect(screen.getByTestId('email-input')).toBeInTheDocument()
  })

  it('reflects aria-invalid when used inside a FormField with an error', () => {
    render(
      <FormField label="Email" error="Invalid email address">
        {(fieldProps) => <TextField {...fieldProps} />}
      </FormField>,
    )
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
  })
})
