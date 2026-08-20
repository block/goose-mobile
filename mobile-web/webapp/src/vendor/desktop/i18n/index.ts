// Minimal i18n surface for the vendored desktop components. They only use
// defineMessages + useIntl, and the web client renders react-intl's
// defaultMessage fallback (no compiled catalogs are shipped). The desktop's
// locale-loading machinery is intentionally omitted here.
export { defineMessages, useIntl } from 'react-intl';
