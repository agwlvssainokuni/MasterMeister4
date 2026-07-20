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
import { Select } from './Select'

const options = [
  { value: 'ja', label: '日本語' },
  { value: 'en', label: 'English' },
]

describe('Select', () => {
  it('renders all options', () => {
    render(<Select options={options} aria-label="Language" />)
    expect(screen.getByRole('option', { name: '日本語' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'English' })).toBeInTheDocument()
  })

  it('allows selecting an option via keyboard', async () => {
    const user = userEvent.setup()
    render(
      <FormField label="Language">
        {(fieldProps) => <Select {...fieldProps} options={options} />}
      </FormField>,
    )

    const select = screen.getByLabelText('Language')
    await user.selectOptions(select, 'en')

    expect(select).toHaveValue('en')
  })

  it('applies the testId as data-testid', () => {
    render(<Select options={options} aria-label="Language" testId="language-select" />)
    expect(screen.getByTestId('language-select')).toBeInTheDocument()
  })
})
