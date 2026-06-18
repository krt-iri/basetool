/*
 * Profit Basetool - squadron-management web app.
 * Copyright (C) 2026 Lucas Greuloch
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.greluc.krt.profit.basetool.frontend.logging;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback pattern layout that scrubs PII / secrets from the rendered console / file log line via
 * the shared {@link PiiMasker}. The masking patterns live in {@link PiiMasker} so this layout and
 * {@link PiiMaskingLogstashEncoder} (the prod JSON sink) apply exactly the same rules (audit M-5).
 */
public class PiiMaskingPatternLayout extends PatternLayout {

  @Override
  public String doLayout(ILoggingEvent event) {
    return PiiMasker.mask(super.doLayout(event));
  }
}
