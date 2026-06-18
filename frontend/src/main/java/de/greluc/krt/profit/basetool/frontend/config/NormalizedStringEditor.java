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

package de.greluc.krt.profit.basetool.frontend.config;

import java.beans.PropertyEditorSupport;
import java.text.Normalizer;
import lombok.RequiredArgsConstructor;

/** Spring Normalized String property editor. */
@RequiredArgsConstructor
public class NormalizedStringEditor extends PropertyEditorSupport {

  private final int maxLength;
  private final boolean emptyAsNull;

  @Override
  public void setAsText(String text) throws IllegalArgumentException {
    if (text == null) {
      setValue(null);
      return;
    }
    String trimmed = text.trim();
    if (emptyAsNull && trimmed.isEmpty()) {
      setValue(null);
      return;
    }
    String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFC);
    if (normalized.length() > maxLength) {
      throw new IllegalArgumentException("String exceeds maximum allowed length of " + maxLength);
    }
    setValue(normalized);
  }
}
