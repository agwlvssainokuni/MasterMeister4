import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { Checkbox } from './Checkbox';

describe('Checkbox', () => {
  it('is labelled and toggleable by clicking the label', async () => {
    const user = userEvent.setup();
    render(<Checkbox label="Remember me" />);

    const checkbox = screen.getByRole('checkbox', { name: 'Remember me' });
    expect(checkbox).not.toBeChecked();

    await user.click(screen.getByText('Remember me'));

    expect(checkbox).toBeChecked();
  });

  it('is toggleable via keyboard', async () => {
    const user = userEvent.setup();
    render(<Checkbox label="Remember me" />);

    await user.tab();
    const checkbox = screen.getByRole('checkbox', { name: 'Remember me' });
    expect(checkbox).toHaveFocus();

    await user.keyboard(' ');
    expect(checkbox).toBeChecked();
  });

  it('applies the testId as data-testid', () => {
    render(<Checkbox label="Remember me" testId="remember-me-checkbox" />);
    expect(screen.getByTestId('remember-me-checkbox')).toBeInTheDocument();
  });
});
