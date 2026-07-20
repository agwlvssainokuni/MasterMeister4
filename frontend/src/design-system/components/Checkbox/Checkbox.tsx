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

import { useId, type InputHTMLAttributes } from 'react'

import styles from './Checkbox.module.css'

export interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: string
  /** data-testid value; naming convention: `{component}-{element-role}` */
  testId?: string
}

export function Checkbox({ label, testId, id, className, ...rest }: CheckboxProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const classes = [styles.input, className].filter(Boolean).join(' ')

  return (
    <label htmlFor={inputId} className={styles.wrapper}>
      <input id={inputId} type="checkbox" className={classes} data-testid={testId} {...rest} />
      <span>{label}</span>
    </label>
  )
}
