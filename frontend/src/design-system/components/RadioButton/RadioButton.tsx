import { useId, type InputHTMLAttributes } from 'react';

import styles from './RadioButton.module.css';

export interface RadioButtonProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: string;
  /** data-testid value; naming convention: `{component}-{element-role}` */
  testId?: string;
}

export function RadioButton({ label, testId, id, className, ...rest }: RadioButtonProps) {
  const generatedId = useId();
  const inputId = id ?? generatedId;
  const classes = [styles.input, className].filter(Boolean).join(' ');

  return (
    <label htmlFor={inputId} className={styles.wrapper}>
      <input id={inputId} type="radio" className={classes} data-testid={testId} {...rest} />
      <span>{label}</span>
    </label>
  );
}
