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
import { afterEach, describe, expect, it } from 'vitest'
import i18n from '../i18n'
import { LanguageSwitcher } from './LanguageSwitcher'

describe('LanguageSwitcher', () => {
  afterEach(async () => {
    await i18n.changeLanguage('ja')
    window.localStorage.removeItem('mastermeister.lang')
  })

  it('選択を変更するとi18nの言語が切り替わる', async () => {
    render(<LanguageSwitcher />)
    const select = screen.getByTestId('language-switcher-select')
    await userEvent.selectOptions(select, 'English')
    expect(i18n.language).toBe('en')
    expect(document.documentElement.getAttribute('lang')).toBe('en')
  })
})
