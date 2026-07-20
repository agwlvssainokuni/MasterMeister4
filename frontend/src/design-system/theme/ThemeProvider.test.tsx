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

import { ThemeProvider, useTheme } from './ThemeProvider'

function Consumer() {
  const { theme, setTheme } = useTheme()
  return (
    <div>
      <span>current: {theme}</span>
      <button onClick={() => setTheme('dark')}>set dark</button>
    </div>
  )
}

describe('ThemeProvider', () => {
  beforeEach(() => {
    window.localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
  })

  afterEach(() => {
    window.localStorage.clear()
    document.documentElement.removeAttribute('data-theme')
  })

  it('defaults to "system" and resolves it onto <html data-theme>', () => {
    render(
      <ThemeProvider>
        <Consumer />
      </ThemeProvider>,
    )
    expect(screen.getByText('current: system')).toBeInTheDocument()
    expect(document.documentElement.getAttribute('data-theme')).toMatch(/^(light|dark)$/)
  })

  it('switches the theme, updates <html data-theme>, and persists to localStorage', async () => {
    const user = userEvent.setup()
    render(
      <ThemeProvider>
        <Consumer />
      </ThemeProvider>,
    )

    await user.click(screen.getByRole('button', { name: 'set dark' }))

    expect(screen.getByText('current: dark')).toBeInTheDocument()
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    expect(window.localStorage.getItem('mastermeister.theme')).toBe('dark')
  })

  it('throws when useTheme is used outside ThemeProvider', () => {
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(() => render(<Consumer />)).toThrow('useTheme must be used within ThemeProvider')
    consoleErrorSpy.mockRestore()
  })
})
