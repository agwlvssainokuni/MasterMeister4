import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { FormField } from '../FormField/FormField';
import { Select } from './Select';

const options = [
  { value: 'ja', label: '日本語' },
  { value: 'en', label: 'English' },
];

describe('Select', () => {
  it('renders all options', () => {
    render(<Select options={options} aria-label="Language" />);
    expect(screen.getByRole('option', { name: '日本語' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'English' })).toBeInTheDocument();
  });

  it('allows selecting an option via keyboard', async () => {
    const user = userEvent.setup();
    render(
      <FormField label="Language">
        {(fieldProps) => <Select {...fieldProps} options={options} />}
      </FormField>,
    );

    const select = screen.getByLabelText('Language');
    await user.selectOptions(select, 'en');

    expect(select).toHaveValue('en');
  });

  it('applies the testId as data-testid', () => {
    render(<Select options={options} aria-label="Language" testId="language-select" />);
    expect(screen.getByTestId('language-select')).toBeInTheDocument();
  });
});
