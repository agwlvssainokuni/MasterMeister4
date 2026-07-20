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

import { RadioButton } from './RadioButton'

describe('RadioButton', () => {
  it('is labelled and selectable within a group', async () => {
    const user = userEvent.setup()
    render(
      <>
        <RadioButton label="MySQL" name="rdbms" value="mysql" />
        <RadioButton label="PostgreSQL" name="rdbms" value="postgresql" />
      </>,
    )

    const mysql = screen.getByRole('radio', { name: 'MySQL' })
    const postgres = screen.getByRole('radio', { name: 'PostgreSQL' })

    await user.click(screen.getByText('PostgreSQL'))

    expect(postgres).toBeChecked()
    expect(mysql).not.toBeChecked()
  })

  it('applies the testId as data-testid', () => {
    render(<RadioButton label="MySQL" testId="rdbms-mysql-radio" />)
    expect(screen.getByTestId('rdbms-mysql-radio')).toBeInTheDocument()
  })
})
