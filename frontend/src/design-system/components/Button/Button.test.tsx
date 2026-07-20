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

import { Button, IconButton } from './Button'

describe('Button', () => {
  it('renders its children', () => {
    render(<Button>Save</Button>)
    expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument()
  })

  it('applies the testId as data-testid', () => {
    render(<Button testId="save-button">Save</Button>)
    expect(screen.getByTestId('save-button')).toBeInTheDocument()
  })

  it('calls onClick when clicked', async () => {
    const user = userEvent.setup()
    const handleClick = vi.fn()
    render(<Button onClick={handleClick}>Save</Button>)

    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(handleClick).toHaveBeenCalledTimes(1)
  })

  it('is keyboard-activatable', async () => {
    const user = userEvent.setup()
    const handleClick = vi.fn()
    render(<Button onClick={handleClick}>Save</Button>)

    await user.tab()
    expect(screen.getByRole('button', { name: 'Save' })).toHaveFocus()
    await user.keyboard('{Enter}')

    expect(handleClick).toHaveBeenCalledTimes(1)
  })

  it('does not fire onClick when disabled', async () => {
    const user = userEvent.setup()
    const handleClick = vi.fn()
    render(
      <Button onClick={handleClick} disabled>
        Save
      </Button>,
    )

    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(handleClick).not.toHaveBeenCalled()
  })

  it('defaults to type="button" so it does not submit an enclosing form', () => {
    render(<Button>Save</Button>)
    expect(screen.getByRole('button', { name: 'Save' })).toHaveAttribute('type', 'button')
  })

  it('is disabled and shows a spinner while loading', () => {
    render(<Button loading>Save</Button>)
    expect(screen.getByRole('button')).toBeDisabled()
    expect(screen.getByRole('status')).toBeInTheDocument()
  })
})

describe('IconButton', () => {
  it('requires and exposes an aria-label', () => {
    render(<IconButton aria-label="削除">x</IconButton>)
    expect(screen.getByRole('button', { name: '削除' })).toBeInTheDocument()
  })

  it('applies the testId as data-testid', () => {
    render(
      <IconButton aria-label="削除" testId="delete-icon-button">
        x
      </IconButton>,
    )
    expect(screen.getByTestId('delete-icon-button')).toBeInTheDocument()
  })
})
