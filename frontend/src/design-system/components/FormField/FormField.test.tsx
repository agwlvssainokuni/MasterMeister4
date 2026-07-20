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
import { describe, expect, it } from 'vitest'

import { FormField } from './FormField'

describe('FormField', () => {
  it('links the label to the field via htmlFor/id', () => {
    render(<FormField label="Email">{(fieldProps) => <input {...fieldProps} />}</FormField>)
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
  })

  it('shows the required indicator when required', () => {
    render(
      <FormField label="Email" required>
        {(fieldProps) => <input {...fieldProps} />}
      </FormField>,
    )
    expect(screen.getByText('Email')).toBeInTheDocument()
    expect(screen.getByText(/必須/)).toBeInTheDocument()
  })

  it('associates the error message via aria-describedby and marks aria-invalid', () => {
    render(
      <FormField label="Email" error="Invalid email address">
        {(fieldProps) => <input {...fieldProps} />}
      </FormField>,
    )
    const input = screen.getByLabelText('Email')
    const error = screen.getByRole('alert')

    expect(error).toHaveTextContent('Invalid email address')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input.getAttribute('aria-describedby')).toContain(error.id)
  })

  it('shows helper text when there is no error', () => {
    render(
      <FormField label="Email" helperText="We will never share your email">
        {(fieldProps) => <input {...fieldProps} />}
      </FormField>,
    )
    expect(screen.getByText('We will never share your email')).toBeInTheDocument()
  })
})
