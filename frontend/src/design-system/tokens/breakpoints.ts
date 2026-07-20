/**
 * NFR-UNIT01-6: desktop and tablet only (no mobile breakpoint).
 *
 * CSS custom properties cannot be referenced inside @media conditions, so these
 * values must be duplicated as literals in each component's CSS Module
 * (e.g. `@media (max-width: 767px) { ... }`). This file is the single source of
 * truth for those literals — keep component media queries in sync with it.
 */
export const breakpoints = {
  tablet: 768,
  desktop: 1024,
} as const;
