import { useTranslation } from 'react-i18next';

import styles from './ErrorBoundary.module.css';

export function DefaultFallback() {
  const { t } = useTranslation();
  return (
    <div className={styles.fallback}>
      <p className={styles.title}>{t('errorBoundary.title')}</p>
      <p className={styles.message}>{t('errorBoundary.message')}</p>
    </div>
  );
}
