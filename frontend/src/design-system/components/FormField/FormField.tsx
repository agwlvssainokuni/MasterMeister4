import { useId, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import styles from './FormField.module.css';

export interface FormFieldRenderProps {
  id: string;
  'aria-describedby'?: string;
  'aria-invalid'?: boolean;
}

export interface FormFieldProps {
  label: string;
  helperText?: string;
  error?: string;
  required?: boolean;
  /** Renders the field's input, wired up with the id/aria-* props needed for the label and error to be announced. */
  children: (fieldProps: FormFieldRenderProps) => ReactNode;
  testId?: string;
}

export function FormField({
  label,
  helperText,
  error,
  required,
  children,
  testId,
}: FormFieldProps) {
  const { t } = useTranslation();
  const id = useId();
  const helperId = helperText ? `${id}-helper` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [helperId, errorId].filter(Boolean).join(' ') || undefined;

  return (
    <div className={styles.field} data-testid={testId}>
      <label htmlFor={id} className={styles.label}>
        {label}
        {required && (
          <span className={styles.required}> ({t('formField.requiredIndicator')})</span>
        )}
      </label>
      {children({
        id,
        'aria-describedby': describedBy,
        'aria-invalid': error ? true : undefined,
      })}
      {helperText && !error && (
        <p id={helperId} className={styles.helper}>
          {helperText}
        </p>
      )}
      {error && (
        <p id={errorId} className={styles.error} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
