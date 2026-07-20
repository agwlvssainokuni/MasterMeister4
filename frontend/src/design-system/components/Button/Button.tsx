import type { ButtonHTMLAttributes, ReactNode } from 'react';

import styles from './Button.module.css';

export type ButtonVariant = 'primary' | 'secondary';

export interface ButtonProps
  extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'type'> {
  children: ReactNode;
  variant?: ButtonVariant;
  type?: 'button' | 'submit';
  /** data-testid value; naming convention: `{component}-{element-role}` */
  testId?: string;
}

export function Button({
  children,
  variant = 'primary',
  type = 'button',
  testId,
  className,
  ...rest
}: ButtonProps) {
  const variantClass = variant === 'primary' ? styles.primary : styles.secondary;
  const classes = [styles.button, variantClass, className].filter(Boolean).join(' ');

  return (
    <button type={type} className={classes} data-testid={testId} {...rest}>
      {children}
    </button>
  );
}
