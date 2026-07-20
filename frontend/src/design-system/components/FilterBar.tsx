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

import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { SearchInput } from './TextInput'
import styles from './FilterBar.module.css'

export interface FilterBarProps {
  searchValue?: string
  onSearchChange?: (value: string) => void
  children?: ReactNode
}

// 簡易版（UNIT-01のスコープ）。実際の絞り込みロジックはデータを扱う
// 後続ユニットでonSearchChange等のコールバックとして提供する。
export function FilterBar({ searchValue, onSearchChange, children }: FilterBarProps) {
  const { t } = useTranslation()
  return (
    <div className={styles.bar}>
      <SearchInput
        className={styles.search}
        placeholder={t('action.search')}
        value={searchValue}
        onChange={(event) => onSearchChange?.(event.target.value)}
        aria-label={t('action.search')}
        data-testid="filter-bar-search-input"
      />
      {children ? <div className={styles.extra}>{children}</div> : null}
    </div>
  )
}
