/*
 * Copyright (C) 2020 Dremio
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
package org.projectnessie.server.providers;

import static org.projectnessie.server.config.VersionStoreConfig.VersionStoreType.DYNAMO;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import org.projectnessie.services.config.ServerConfig;
import org.projectnessie.versioned.StoreWorker;
import org.projectnessie.versioned.VersionStore;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapter;
import org.projectnessie.versioned.persist.dynamodb.DynamoDatabaseAdapterFactory;
import org.projectnessie.versioned.persist.dynamodb.DynamoDatabaseClient;
import org.projectnessie.versioned.persist.dynamodb.ImmutableProvidedDynamoClientConfig;
import org.projectnessie.versioned.persist.store.PersistVersionStore;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/** DynamoDB version store factory. */
@StoreType(DYNAMO)
@Dependent
public class DynamoVersionStoreFactory implements VersionStoreFactory {
  private final DynamoDbClient dynamoDbClient;

  /** Creates a factory for dynamodb version stores. */
  @Inject
  public DynamoVersionStoreFactory(DynamoDbClient dynamoDbClient) {
    this.dynamoDbClient = dynamoDbClient;
  }

  @Override
  public <VALUE, METADATA, VALUE_TYPE extends Enum<VALUE_TYPE>>
      VersionStore<VALUE, METADATA, VALUE_TYPE> newStore(
          StoreWorker<VALUE, METADATA, VALUE_TYPE> worker, ServerConfig serverConfig) {

    DatabaseAdapter databaseAdapter =
        new DynamoDatabaseAdapterFactory()
            .newBuilder()
            .configure(
                c -> {
                  DynamoDatabaseClient client = new DynamoDatabaseClient();
                  client.configure(
                      ImmutableProvidedDynamoClientConfig.builder()
                          .dynamoDbClient(dynamoDbClient)
                          .build());
                  client.initialize();
                  return c.withConnectionProvider(client);
                })
            .build();

    databaseAdapter.initializeRepo(serverConfig.getDefaultBranch());

    return new PersistVersionStore<>(databaseAdapter, worker);
  }
}
