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

import type { SelectHTMLAttributes } from 'react'

import { ChevronDownIcon } from '../icons/ChevronDownIcon'
import styles from './Select.module.css'

export interface SelectOption {
  value: string
  label: string
}

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  options: SelectOption[]
  /** data-testid value; naming convention: `{component}-{element-role}` */
  testId?: string
}

export function Select({ options, testId, className, ...rest }: SelectProps) {
  const classes = [styles.select, className].filter(Boolean).join(' ')
  return (
    <span className={styles.wrapper}>
      <select className={classes} data-testid={testId} {...rest}>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      <ChevronDownIcon className={styles.icon} />
    </span>
  )
}
