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

// No language switcher exists yet (out of scope for UNIT-01 / STORY-0.1).
// Default to Japanese; a later unit can add detection/switching on top of
// this same i18next instance without changing the resource structure.
void i18next.use(initReactI18next).init({
  lng: 'ja',
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

export default i18next
