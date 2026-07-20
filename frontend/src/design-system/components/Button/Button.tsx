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

import type { ButtonHTMLAttributes, ReactNode } from 'react'

import { Spinner } from '../Spinner'
import styles from './Button.module.css'

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost'
export type ButtonSize = 'sm' | 'md'

export interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'type'> {
  children: ReactNode
  variant?: ButtonVariant
  size?: ButtonSize
  loading?: boolean
  type?: 'button' | 'submit'
  /** data-testid value; naming convention: `{component}-{element-role}` */
  testId?: string
}

export function Button({
  children,
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled,
  type = 'button',
  testId,
  className,
  ...rest
}: ButtonProps) {
  const classes = [styles.button, styles[variant], styles[size], className]
    .filter(Boolean)
    .join(' ')

  return (
    <button
      type={type}
      className={classes}
      disabled={disabled || loading}
      data-testid={testId}
      {...rest}
    >
      {loading ? <Spinner size="sm" /> : null}
      <span className={loading ? styles.loadingLabel : undefined}>{children}</span>
    </button>
  )
}

export interface IconButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'type'> {
  'aria-label': string
  variant?: ButtonVariant
  size?: ButtonSize
  children: ReactNode
  type?: 'button' | 'submit'
  /** data-testid value; naming convention: `{component}-{element-role}` */
  testId?: string
}

export function IconButton({
  variant = 'ghost',
  size = 'md',
  type = 'button',
  testId,
  className,
  children,
  ...rest
}: IconButtonProps) {
  const classes = [styles.button, styles.icon, styles[variant], styles[size], className]
    .filter(Boolean)
    .join(' ')
  return (
    <button type={type} className={classes} data-testid={testId} {...rest}>
      {children}
    </button>
  )
}
