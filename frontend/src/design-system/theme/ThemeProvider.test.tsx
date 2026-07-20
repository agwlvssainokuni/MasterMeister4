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
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { ThemeProvider, useTheme } from './ThemeProvider'

function Consumer() {
  const { theme, setTheme } = useTheme()
  return (
    <div>
      <span data-testid="theme-value">{theme}</span>
      <button onClick={() => setTheme('dark')}>dark</button>
      <button onClick={() => setTheme('light')}>light</button>
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

  it('デフォルトはsystemで、html要素にdata-theme属性が設定される', () => {
    render(
      <ThemeProvider>
        <Consumer />
      </ThemeProvider>,
    )
    expect(screen.getByTestId('theme-value')).toHaveTextContent('system')
    expect(document.documentElement.getAttribute('data-theme')).toMatch(/light|dark/)
  })

  it('setThemeでテーマを変更するとdata-theme属性とlocalStorageが更新される', async () => {
    render(
      <ThemeProvider>
        <Consumer />
      </ThemeProvider>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'dark' }))
    expect(screen.getByTestId('theme-value')).toHaveTextContent('dark')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
    expect(window.localStorage.getItem('mastermeister.theme')).toBe('dark')
  })

  it('localStorageに保存済みのテーマを初期値として復元する', () => {
    window.localStorage.setItem('mastermeister.theme', 'light')
    render(
      <ThemeProvider>
        <Consumer />
      </ThemeProvider>,
    )
    expect(screen.getByTestId('theme-value')).toHaveTextContent('light')
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
  })

  it('ProviderなしでuseThemeを呼ぶとエラーになる', () => {
    const consoleError = console.error
    console.error = () => {}
    expect(() => render(<Consumer />)).toThrow('useTheme must be used within ThemeProvider')
    console.error = consoleError
  })
})
