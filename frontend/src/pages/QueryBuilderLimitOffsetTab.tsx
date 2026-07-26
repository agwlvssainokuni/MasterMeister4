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

import { useTranslation } from 'react-i18next'
import { FormField, TextInput } from '../design-system/components'

// frontend-components.md 画面2 LIMIT OFFSETタブ。数値入力2件、いずれも任意。
export function QueryBuilderLimitOffsetTab({
  limit,
  offset,
  onChangeLimit,
  onChangeOffset,
}: {
  limit: number | null
  offset: number | null
  onChangeLimit: (value: number | null) => void
  onChangeOffset: (value: number | null) => void
}) {
  const { t } = useTranslation()

  const parseValue = (raw: string): number | null => {
    if (raw === '') {
      return null
    }
    const parsed = Number(raw)
    return Number.isFinite(parsed) ? parsed : null
  }

  return (
    <div>
      <FormField label={t('queryBuilder.limitOffset.limit')}>
        <TextInput
          type="number"
          min={0}
          value={limit ?? ''}
          onChange={(e) => onChangeLimit(parseValue(e.target.value))}
        />
      </FormField>
      <FormField label={t('queryBuilder.limitOffset.offset')}>
        <TextInput
          type="number"
          min={0}
          value={offset ?? ''}
          onChange={(e) => onChangeOffset(parseValue(e.target.value))}
        />
      </FormField>
    </div>
  )
}
