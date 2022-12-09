/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

import java.util.List;

public class StaleDevices {

  private List<Integer> staleDevices;

  public List<Integer> getStaleDevices() {
    return staleDevices;
  }
}