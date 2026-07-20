import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { FormField } from '../FormField/FormField';
import { TextField } from './TextField';

describe('TextField', () => {
  it('accepts typed input', async () => {
    const user = userEvent.setup();
    render(
      <FormField label="Email">
        {(fieldProps) => <TextField {...fieldProps} />}
      </FormField>,
    );

    const input = screen.getByLabelText('Email');
    await user.type(input, 'user@example.com');

    expect(input).toHaveValue('user@example.com');
  });

  it('applies the testId as data-testid', () => {
    render(<TextField testId="email-input" />);
    expect(screen.getByTestId('email-input')).toBeInTheDocument();
  });

  it('reflects aria-invalid when used inside a FormField with an error', () => {
    render(
      <FormField label="Email" error="Invalid email address">
        {(fieldProps) => <TextField {...fieldProps} />}
      </FormField>,
    );
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
  });
});
