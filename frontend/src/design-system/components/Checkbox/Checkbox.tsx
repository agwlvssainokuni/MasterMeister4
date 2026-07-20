import { useId, type InputHTMLAttributes } from 'react';

import styles from './Checkbox.module.css';

export interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: string;
  /** data-testid value; naming convention: `{component}-{element-role}` */
  testId?: string;
}

export function Checkbox({ label, testId, id, className, ...rest }: CheckboxProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const classes = [styles.input, className].filter(Boolean).join(' ');

  return (
    <label htmlFor={inputId} className={styles.wrapper}>
      <input id={inputId} type="checkbox" className={classes} data-testid={testId} {...rest} />
      <span>{label}</span>
    </label>
  );
}
