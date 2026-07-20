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

import type { TextareaHTMLAttributes } from 'react'

import styles from './TextArea.module.css'

export interface TextAreaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  /** Uses the monospace font token; intended for raw SQL entry (e.g. STORY-7.1). */
  monospace?: boolean
  /** data-testid value; naming convention: `{component}-{element-role}` */
  testId?: string
}

export function TextArea({ monospace, testId, className, style, ...rest }: TextAreaProps) {
  const classes = [styles.textArea, className].filter(Boolean).join(' ')
  const mergedStyle = monospace ? { fontFamily: 'var(--mm-font-mono)', ...style } : style

  return <textarea className={classes} style={mergedStyle} data-testid={testId} {...rest} />
}
