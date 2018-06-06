/**
 * Copyright 2016-2018 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package zipkin2.propagation.stackdriver;

import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.logging.Level;
import java.util.logging.Logger;

import static brave.internal.HexCodec.lenientLowerHexToUnsignedLong;

public final class XCloudTraceContextExtractor<C, K> implements TraceContext.Extractor<C> {

  private static final Logger LOG = Logger.getLogger(XCloudTraceContextExtractor.class.getName());

  private final StackdriverTracePropagation<K> propagation;
  private final Propagation.Getter<C, K> getter;

  XCloudTraceContextExtractor(StackdriverTracePropagation<K> propagation,
      Propagation.Getter<C, K> getter) {
    this.propagation = propagation;
    this.getter = getter;
  }

  @Override public TraceContextOrSamplingFlags extract(C carrier) {
    if (carrier == null) throw new NullPointerException("carrier == null");

    TraceContextOrSamplingFlags result = TraceContextOrSamplingFlags.EMPTY;

    String xCloudTraceContext = getter.get(carrier, propagation.getTraceIdKey());

    if (xCloudTraceContext != null) {
      String[] tokens = xCloudTraceContext.split("/");

      // Try to parse the trace IDs into the context
      TraceContext.Builder context = TraceContext.newBuilder();

      if (tokens.length >= 2) {
        long[] traceId = convertHexTraceIdToLong(tokens[0]);
        int semicolonPos = tokens[1].indexOf(";");
        String spanId = semicolonPos == -1
            ? tokens[1]
            : tokens[1].substring(0, semicolonPos);
        if (traceId != null) {
          result = TraceContextOrSamplingFlags.create(
              context.traceIdHigh(traceId[0])
                  .traceId(traceId[1])
                  .spanId(Long.parseLong(spanId)).build());
        }
      }
    }

    return result;
  }

  private long[] convertHexTraceIdToLong(String hexTraceId) {
    long[] result = new long[2];
    int length = hexTraceId.length();

    if (length != 32) return null;

    // left-most characters, if any, are the high bits
    int traceIdIndex = Math.max(0, length - 16);

    result[0] = lenientLowerHexToUnsignedLong(hexTraceId, 0, traceIdIndex);
    if (result[0] == 0) {
      if (LOG.isLoggable(Level.FINE)) {
        LOG.fine(hexTraceId + " is not a lower hex string.");
      }
      return null;
    }

    // right-most up to 16 characters are the low bits
    result[1] = lenientLowerHexToUnsignedLong(hexTraceId, traceIdIndex, length);
    if (result[1] == 0) {
      if (LOG.isLoggable(Level.FINE)) {
        LOG.fine(hexTraceId + " is not a lower hex string.");
      }
      return null;
    }
    return result;
  }
}
