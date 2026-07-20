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

import type { SVGAttributes } from 'react'

// サードパーティのアイコンライブラリは使用せず、自作SVGアイコンセットとして提供する（FR-0.1）。
// 一覧はaidlc-docs/construction/plans/unit-01-code-generation-plan.mdの
// 「Icon一覧（ベースライン）」を参照。過不足は随時追加する。
export type IconName =
  | 'menu'
  | 'chevron-left'
  | 'chevron-right'
  | 'chevron-down'
  | 'chevron-up'
  | 'close'
  | 'sun'
  | 'moon'
  | 'user'
  | 'logout'
  | 'globe'
  | 'check'
  | 'check-circle'
  | 'warning-triangle'
  | 'x-circle'
  | 'info'
  | 'edit'
  | 'delete'
  | 'add'
  | 'save'
  | 'search'
  | 'copy'
  | 'wrap-text'
  | 'sort'
  | 'eye'
  | 'eye-off'

export interface IconProps extends SVGAttributes<SVGSVGElement> {
  name: IconName
  size?: number
}

function renderPaths(name: IconName) {
  switch (name) {
    case 'menu':
      return (
        <>
          <line x1="3" y1="6" x2="21" y2="6" />
          <line x1="3" y1="12" x2="21" y2="12" />
          <line x1="3" y1="18" x2="21" y2="18" />
        </>
      )
    case 'chevron-left':
      return <polyline points="15 18 9 12 15 6" />
    case 'chevron-right':
      return <polyline points="9 18 15 12 9 6" />
    case 'chevron-down':
      return <polyline points="6 9 12 15 18 9" />
    case 'chevron-up':
      return <polyline points="18 15 12 9 6 15" />
    case 'close':
      return (
        <>
          <line x1="18" y1="6" x2="6" y2="18" />
          <line x1="6" y1="6" x2="18" y2="18" />
        </>
      )
    case 'sun':
      return (
        <>
          <circle cx="12" cy="12" r="4" />
          <line x1="12" y1="2" x2="12" y2="4" />
          <line x1="12" y1="20" x2="12" y2="22" />
          <line x1="4" y1="12" x2="2" y2="12" />
          <line x1="22" y1="12" x2="20" y2="12" />
          <line x1="5" y1="5" x2="6.5" y2="6.5" />
          <line x1="17.5" y1="17.5" x2="19" y2="19" />
          <line x1="5" y1="19" x2="6.5" y2="17.5" />
          <line x1="17.5" y1="6.5" x2="19" y2="5" />
        </>
      )
    case 'moon':
      return <path d="M21 12.5A9 9 0 1 1 11.5 3 7 7 0 0 0 21 12.5Z" />
    case 'user':
      return (
        <>
          <circle cx="12" cy="8" r="4" />
          <path d="M4 21c0-4.4 3.6-8 8-8s8 3.6 8 8" />
        </>
      )
    case 'logout':
      return (
        <>
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
          <polyline points="16 17 21 12 16 7" />
          <line x1="21" y1="12" x2="9" y2="12" />
        </>
      )
    case 'globe':
      return (
        <>
          <circle cx="12" cy="12" r="9" />
          <line x1="3" y1="12" x2="21" y2="12" />
          <path d="M12 3a14 14 0 0 1 0 18 14 14 0 0 1 0-18Z" />
        </>
      )
    case 'check':
      return <polyline points="20 6 9 17 4 12" />
    case 'check-circle':
      return (
        <>
          <circle cx="12" cy="12" r="9" />
          <polyline points="8 12 11 15 16 9" />
        </>
      )
    case 'warning-triangle':
      return (
        <>
          <path d="M12 3 2 20h20L12 3Z" />
          <line x1="12" y1="10" x2="12" y2="14" />
          <line x1="12" y1="17" x2="12" y2="17.01" />
        </>
      )
    case 'x-circle':
      return (
        <>
          <circle cx="12" cy="12" r="9" />
          <line x1="9" y1="9" x2="15" y2="15" />
          <line x1="15" y1="9" x2="9" y2="15" />
        </>
      )
    case 'info':
      return (
        <>
          <circle cx="12" cy="12" r="9" />
          <line x1="12" y1="11" x2="12" y2="16" />
          <line x1="12" y1="7.5" x2="12" y2="7.51" />
        </>
      )
    case 'edit':
      return (
        <>
          <path d="M12 20h9" />
          <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
        </>
      )
    case 'delete':
      return (
        <>
          <polyline points="3 6 5 6 21 6" />
          <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m2 0v14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V6h12Z" />
        </>
      )
    case 'add':
      return (
        <>
          <line x1="12" y1="5" x2="12" y2="19" />
          <line x1="5" y1="12" x2="19" y2="12" />
        </>
      )
    case 'save':
      return (
        <>
          <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z" />
          <polyline points="17 21 17 13 7 13 7 21" />
          <polyline points="7 3 7 8 15 8" />
        </>
      )
    case 'search':
      return (
        <>
          <circle cx="11" cy="11" r="7" />
          <line x1="21" y1="21" x2="16.65" y2="16.65" />
        </>
      )
    case 'copy':
      return (
        <>
          <rect x="9" y="9" width="12" height="12" rx="2" />
          <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
        </>
      )
    case 'wrap-text':
      return (
        <>
          <line x1="3" y1="6" x2="21" y2="6" />
          <path d="M3 12h15a3 3 0 0 1 0 6h-4" />
          <polyline points="17 15 14 18 17 21" />
          <line x1="3" y1="18" x2="10" y2="18" />
        </>
      )
    case 'sort':
      return (
        <>
          <path d="M8 9l4-4 4 4" />
          <path d="M16 15l-4 4-4-4" />
        </>
      )
    case 'eye':
      return (
        <>
          <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7Z" />
          <circle cx="12" cy="12" r="3" />
        </>
      )
    case 'eye-off':
      return (
        <>
          <path d="M17.94 17.94A10.94 10.94 0 0 1 12 19c-7 0-11-7-11-7a21.6 21.6 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 7 11 7a21.6 21.6 0 0 1-3.22 4.5" />
          <line x1="1" y1="1" x2="23" y2="23" />
        </>
      )
  }
}

export function Icon({ name, size = 16, ...rest }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      role="presentation"
      aria-hidden="true"
      {...rest}
    >
      {renderPaths(name)}
    </svg>
  )
}
