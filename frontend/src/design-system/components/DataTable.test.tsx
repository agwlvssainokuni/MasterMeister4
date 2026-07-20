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
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { DataTable } from './DataTable'
import type { TableColumn } from './DataTable'

interface Row {
  id: string
  name: string
}

const columns: readonly TableColumn<Row>[] = [
  { key: 'id', header: 'ID', render: (row) => row.id, sortable: true },
  { key: 'name', header: '名前', render: (row) => row.name },
]

const rows: readonly Row[] = [
  { id: '1', name: '顧客マスタ' },
  { id: '2', name: '商品マスタ' },
]

describe('DataTable', () => {
  it('ソートボタンでonSortChangeが呼ばれaria-sortが付く', async () => {
    const onSortChange = vi.fn()
    render(
      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(row) => row.id}
        sortKey="id"
        sortDirection="asc"
        onSortChange={onSortChange}
      />,
    )
    const header = screen.getByRole('columnheader', { name: /ID/ })
    expect(header).toHaveAttribute('aria-sort', 'ascending')
    await userEvent.click(within(header).getByRole('button'))
    expect(onSortChange).toHaveBeenCalledWith('id', 'desc')
  })

  it('行選択と全選択が動作する', async () => {
    function Harness() {
      const [selected, setSelected] = useState<ReadonlySet<string>>(new Set())
      return (
        <DataTable
          columns={columns}
          rows={rows}
          rowKey={(row) => row.id}
          selectable
          selectedKeys={selected}
          onSelectionChange={setSelected}
        />
      )
    }
    render(<Harness />)
    const rowCheckboxes = screen.getAllByRole('checkbox', { name: 'この行を選択' })
    await userEvent.click(rowCheckboxes[0])
    expect(rowCheckboxes[0]).toBeChecked()

    const selectAll = screen.getByRole('checkbox', { name: 'すべて選択' })
    await userEvent.click(selectAll)
    expect(screen.getAllByRole('checkbox', { name: 'この行を選択' }).every((c) => c)).toBe(true)
  })

  it('0件のときEmptyStateを表示する', () => {
    render(<DataTable columns={columns} rows={[]} rowKey={(row: Row) => row.id} />)
    expect(screen.getByTestId('empty-state')).toBeInTheDocument()
  })
})
