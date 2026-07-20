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
import { Button } from './Button'
import { ConfirmDialog, Modal } from './Modal'

describe('Modal', () => {
  it('open=falseでは表示されない', () => {
    render(
      <Modal open={false} title="確認" onClose={() => undefined}>
        本文
      </Modal>,
    )
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('Escでoncloseが呼ばれる', async () => {
    const onClose = vi.fn()
    render(
      <Modal open title="確認" onClose={onClose}>
        本文
      </Modal>,
    )
    await userEvent.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('Tabがダイアログ内で循環する（フォーカストラップ）', async () => {
    render(
      <Modal
        open
        title="確認"
        onClose={() => undefined}
        footer={<Button variant="primary">OK</Button>}
      >
        本文
      </Modal>,
    )
    const closeButton = screen.getByRole('button', { name: '閉じる' })
    const okButton = screen.getByRole('button', { name: 'OK' })
    expect(closeButton).toHaveFocus()

    await userEvent.tab()
    expect(okButton).toHaveFocus()

    await userEvent.tab()
    expect(closeButton).toHaveFocus()

    await userEvent.tab({ shift: true })
    expect(okButton).toHaveFocus()
  })

  it('aria-modalとaria-labelledbyが設定される', () => {
    render(
      <Modal open title="確認ダイアログ" onClose={() => undefined}>
        本文
      </Modal>,
    )
    const dialog = screen.getByRole('dialog')
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(dialog).toHaveAccessibleName('確認ダイアログ')
  })
})

describe('ConfirmDialog', () => {
  it('確認・キャンセルでそれぞれコールバックが呼ばれる', async () => {
    const onConfirm = vi.fn()
    const onCancel = vi.fn()
    render(
      <ConfirmDialog
        open
        title="削除確認"
        message="本当に削除しますか？"
        tone="danger"
        onConfirm={onConfirm}
        onCancel={onCancel}
      />,
    )
    await userEvent.click(screen.getByRole('button', { name: 'キャンセル' }))
    expect(onCancel).toHaveBeenCalledTimes(1)

    await userEvent.click(screen.getByRole('button', { name: 'OK' }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })
})
