import type { SelectHTMLAttributes } from 'react';

import { ChevronDownIcon } from '../icons/ChevronDownIcon';
import styles from './Select.module.css';

export interface SelectOption {
  value: string;
  label: string;
}

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  options: SelectOption[];
  /** data-testid value; naming convention: `{component}-{element-role}` */
  testId?: string;
}

export function Select({ options, testId, className, ...rest }: SelectProps) {
  const classes = [styles.select, className].filter(Boolean).join(' ');
  return (
    <span className={styles.wrapper}>
      <select className={classes} data-testid={testId} {...rest}>
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
      <ChevronDownIcon className={styles.icon} />
    </span>
  );
}
