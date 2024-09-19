/*
 * Copyright (C) 2024 Dremio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.projectnessie.catalog.files.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.time.Duration;
import java.util.Optional;
import org.projectnessie.nessie.immutables.NessieImmutable;

@NessieImmutable
@JsonSerialize(as = ImmutableGcsDownscopedCredentials.class)
@JsonDeserialize(as = ImmutableGcsDownscopedCredentials.class)
public interface GcsDownscopedCredentials {
  /**
   * Flag to enable the currently experimental option to send short-lived and scoped-down
   * credentials to clients.
   *
   * <p>The current default is to not enable short-lived and scoped-down credentials, but the
   * default may change to enable in the future.
   */
  Optional<Boolean> enable();

  /**
   * The expiration margin for the scoped down OAuth2 token.
   *
   * <p>Defaults to the Google defaults.
   */
  Optional<Duration> expirationMargin();

  /**
   * The refresh margin for the scoped down OAuth2 token.
   *
   * <p>Defaults to the Google defaults.
   */
  Optional<Duration> refreshMargin();
}