import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { RadioButton } from './RadioButton';

describe('RadioButton', () => {
  it('is labelled and selectable within a group', async () => {
    const user = userEvent.setup();
    render(
      <>
        <RadioButton label="MySQL" name="rdbms" value="mysql" />
        <RadioButton label="PostgreSQL" name="rdbms" value="postgresql" />
      </>,
    );

    const mysql = screen.getByRole('radio', { name: 'MySQL' });
    const postgres = screen.getByRole('radio', { name: 'PostgreSQL' });

    await user.click(screen.getByText('PostgreSQL'));

    expect(postgres).toBeChecked();
    expect(mysql).not.toBeChecked();
  });

  it('applies the testId as data-testid', () => {
    render(<RadioButton label="MySQL" testId="rdbms-mysql-radio" />);
    expect(screen.getByTestId('rdbms-mysql-radio')).toBeInTheDocument();
  });
});
