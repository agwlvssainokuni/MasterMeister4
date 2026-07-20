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

import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { Alert, Badge, Card, CodeBlock, EmptyState, KeyValueList } from './Display'

describe('Badge', () => {
  it('内容を表示する', () => {
    render(<Badge tone="success">承認済み</Badge>)
    expect(screen.getByText('承認済み')).toBeInTheDocument()
  })
})

describe('Alert', () => {
  it('role=alertでtoneに応じた内容を表示する', () => {
    render(<Alert tone="danger">エラーが発生しました</Alert>)
    expect(screen.getByRole('alert')).toHaveTextContent('エラーが発生しました')
  })
})

describe('Card', () => {
  it('titleとchildrenを表示する', () => {
    render(<Card title="タイトル">本文</Card>)
    expect(screen.getByText('タイトル')).toBeInTheDocument()
    expect(screen.getByText('本文')).toBeInTheDocument()
  })
})

describe('EmptyState', () => {
  it('messageを指定しない場合は既定文言を表示する', () => {
    render(<EmptyState />)
    expect(screen.getByTestId('empty-state')).toHaveTextContent('データがありません')
  })

  it('messageを指定した場合はそれを表示する', () => {
    render(<EmptyState message="対象データがありません" />)
    expect(screen.getByTestId('empty-state')).toHaveTextContent('対象データがありません')
  })
})

describe('CodeBlock', () => {
  it('コードを表示し、コピーボタンでclipboardへ書き込む', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, { clipboard: { writeText } })
    render(<CodeBlock code="SELECT 1" />)
    expect(screen.getByText('SELECT 1')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'コピー' }))
    expect(writeText).toHaveBeenCalledWith('SELECT 1')
  })
})

describe('KeyValueList', () => {
  it('key/valueの一覧を表示する', () => {
    render(<KeyValueList items={[{ key: '接続名', value: '本番DB' }]} />)
    expect(screen.getByText('接続名')).toBeInTheDocument()
    expect(screen.getByText('本番DB')).toBeInTheDocument()
  })
})
