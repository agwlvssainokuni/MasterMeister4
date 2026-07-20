/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
} as const
