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

import { Badge } from './Badge'

describe('Badge', () => {
  it('renders its children', () => {
    render(<Badge tone="success">有効</Badge>)
    expect(screen.getByText('有効')).toBeInTheDocument()
  })

  it('applies the testId as data-testid', () => {
    render(<Badge testId="status-badge">有効</Badge>)
    expect(screen.getByTestId('status-badge')).toBeInTheDocument()
  })
})
