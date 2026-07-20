import i18next from 'i18next';
import { initReactI18next } from 'react-i18next';

import commonJa from './locales/ja/common.json';
import commonEn from './locales/en/common.json';

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
});

export default i18next;
