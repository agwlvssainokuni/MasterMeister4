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
import { TextArea } from './TextArea'

describe('TextArea', () => {
  it('accepts multi-line typed input', async () => {
    const user = userEvent.setup()
    render(<FormField label="SQL">{(fieldProps) => <TextArea {...fieldProps} />}</FormField>)

    const textarea = screen.getByLabelText('SQL')
    await user.type(textarea, 'SELECT 1{enter}FROM dual')

    expect(textarea).toHaveValue('SELECT 1\nFROM dual')
  })

  it('applies the testId as data-testid', () => {
    render(<TextArea testId="sql-input-textarea" />)
    expect(screen.getByTestId('sql-input-textarea')).toBeInTheDocument()
  })

  it('reflects aria-invalid when used inside a FormField with an error', () => {
    render(
      <FormField label="SQL" error="SQLを入力してください">
        {(fieldProps) => <TextArea {...fieldProps} />}
      </FormField>,
    )
    expect(screen.getByLabelText('SQL')).toHaveAttribute('aria-invalid', 'true')
  })

  it('switches to the monospace font token when monospace is set', () => {
    render(<TextArea monospace testId="sql-textarea" />)
    expect(screen.getByTestId('sql-textarea')).toHaveStyle({
      fontFamily: 'var(--font-family-mono)',
    })
  })
})
