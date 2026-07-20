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

import { Alert } from './Alert'

describe('Alert', () => {
  it('renders with an alert role', () => {
    render(<Alert tone="danger">保存に失敗しました</Alert>)
    expect(screen.getByRole('alert')).toHaveTextContent('保存に失敗しました')
  })

  it('applies the testId as data-testid', () => {
    render(<Alert testId="save-error-alert">保存に失敗しました</Alert>)
    expect(screen.getByTestId('save-error-alert')).toBeInTheDocument()
  })
})
