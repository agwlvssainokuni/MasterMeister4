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

import { useState } from 'react'

import { Button } from '../components/Button'
import { Checkbox } from '../components/Checkbox'
import { ErrorBoundary } from '../components/ErrorBoundary'
import { FormField } from '../components/FormField'
import { RadioButton } from '../components/RadioButton'
import { Select } from '../components/Select'
import { TextArea } from '../components/TextArea'
import { TextField } from '../components/TextField'
import styles from './CatalogPage.module.css'

const RDBMS_OPTIONS = [
  { value: 'mysql', label: 'MySQL' },
  { value: 'postgresql', label: 'PostgreSQL' },
]

function Bomb(): never {
  throw new Error('Catalog demo error')
}

// Lightweight in-app substitute for a dedicated tool (e.g. Storybook): NFR-UNIT01-4
// still holds — TypeScript types and *.test.tsx remain the source of truth for each
// component's contract. This page exists purely so a person can see every common
// component's visual states in one place without reading test files.
export function CatalogPage() {
  const [showError, setShowError] = useState(false)

  return (
    <main className={styles.page}>
      <h1 className={styles.title}>コンポーネントカタログ</h1>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Button</h2>
        <div className={styles.row}>
          <Button variant="primary">Primary</Button>
          <Button variant="secondary">Secondary</Button>
          <Button variant="primary" disabled>
            Disabled
          </Button>
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>TextField / FormField</h2>
        <FormField label="メールアドレス" helperText="ヘルプテキストの表示例">
          {(fieldProps) => <TextField {...fieldProps} type="email" />}
        </FormField>
        <FormField
          label="メールアドレス（エラー state）"
          error="有効なメールアドレスを入力してください"
        >
          {(fieldProps) => <TextField {...fieldProps} type="email" />}
        </FormField>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>TextArea</h2>
        <FormField label="SQL" helperText="monospaceを指定するとNoto Sans Monoで表示されます">
          {(fieldProps) => (
            <TextArea {...fieldProps} monospace defaultValue={'SELECT id, name\nFROM 顧客'} />
          )}
        </FormField>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Select</h2>
        <FormField label="対象RDBMS">
          {(fieldProps) => <Select {...fieldProps} options={RDBMS_OPTIONS} />}
        </FormField>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Checkbox</h2>
        <div className={styles.row}>
          <Checkbox label="未チェック" />
          <Checkbox label="チェック済み" defaultChecked />
          <Checkbox label="無効化" disabled />
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>RadioButton</h2>
        <div className={styles.row}>
          {RDBMS_OPTIONS.map((option) => (
            <RadioButton
              key={option.value}
              label={option.label}
              name="catalog-rdbms"
              value={option.value}
            />
          ))}
        </div>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>ErrorBoundary</h2>
        <p>
          「エラーを発生させる」を押すと、このセクションだけがフォールバック表示に切り替わります。
        </p>
        <Button variant="secondary" onClick={() => setShowError(true)}>
          エラーを発生させる
        </Button>
        {/* A boundary local to this demo section, separate from the app's single
            top-level instance (DP-UNIT01-1), so triggering it here doesn't take
            down the rest of the catalog page. */}
        <ErrorBoundary>{showError ? <Bomb /> : <p>（正常時の表示）</p>}</ErrorBoundary>
      </section>
    </main>
  )
}
