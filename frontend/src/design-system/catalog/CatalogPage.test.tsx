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
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ThemeProvider } from '../theme/ThemeProvider'
import { CatalogPage } from './CatalogPage'

function renderCatalogPage() {
  return render(
    <ThemeProvider>
      <CatalogPage />
    </ThemeProvider>,
  )
}

describe('CatalogPage', () => {
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    consoleErrorSpy.mockRestore()
    window.localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
  })

  it('renders a section for every common component', () => {
    renderCatalogPage()

    for (const heading of [
      'Button',
      'Spinner',
      'TextField / FormField',
      'PasswordInput / SearchInput',
      'TextArea',
      'Select',
      'Checkbox',
      'RadioButton',
      'Switch',
      'Badge',
      'Alert',
      'Card',
      'EmptyState',
      'ErrorBoundary',
    ]) {
      expect(screen.getByRole('heading', { name: heading })).toBeInTheDocument()
    }
  })

  it('demonstrates the ErrorBoundary fallback without affecting the rest of the page', async () => {
    const user = userEvent.setup()
    renderCatalogPage()

    await user.click(screen.getByRole('button', { name: 'エラーを発生させる' }))

    expect(screen.getByText('問題が発生しました')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Button' })).toBeInTheDocument()
  })
})
