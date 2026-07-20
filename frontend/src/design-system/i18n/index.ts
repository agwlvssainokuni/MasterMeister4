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

import i18next from 'i18next'
import { initReactI18next } from 'react-i18next'

import commonJa from './locales/ja/common.json'
import commonEn from './locales/en/common.json'

export type Language = 'ja' | 'en'

const STORAGE_KEY = 'mastermeister.lang'

export function detectInitialLanguage(): Language {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    if (stored === 'ja' || stored === 'en') {
      return stored
    }
  } catch {
    /* localStorage 不可の環境ではブラウザ言語に従う */
  }
  return navigator.language.toLowerCase().startsWith('ja') ? 'ja' : 'en'
}

export function changeLanguage(language: Language): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, language)
  } catch {
    /* 保存不可でも切替は継続 */
  }
  void i18next.changeLanguage(language)
}

void i18next.use(initReactI18next).init({
  lng: detectInitialLanguage(),
  fallbackLng: 'en',
  defaultNS: 'common',
  resources: {
    ja: { common: commonJa },
    en: { common: commonEn },
  },
  interpolation: {
    escapeValue: false, // React already escapes output
  },
})

i18next.on('languageChanged', (language) => {
  document.documentElement.setAttribute('lang', language)
})
document.documentElement.setAttribute('lang', i18next.language)

export default i18next
