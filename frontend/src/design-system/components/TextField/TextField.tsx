import type { InputHTMLAttributes } from 'react';

import styles from './TextField.module.css';

export interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  /** data-testid value; naming convention: `{component}-{element-role}` */
  testId?: string;
}

export function TextField({ testId, className, ...rest }: TextFieldProps) {
  const classes = [styles.textField, className].filter(Boolean).join(' ');
  return <input className={classes} data-testid={testId} {...rest} />;
}
