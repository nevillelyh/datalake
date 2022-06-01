package io.datalake.trino;

import io.trino.spi.security.GroupProvider;
import io.trino.spi.security.GroupProviderFactory;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class DatalakeGroupProviderFactory implements GroupProviderFactory {
  @Override
  public String getName() {
    return "datalake";
  }

  @Override
  public GroupProvider create(Map<String, String> config) {
    return new GroupProvider() {
      private final Map<String, Set<String>> groups = Map.of(
          "trino", Set.of("owner"),
          "alice", Set.of("hive_owner", "elastic_viewer"),
          "bob", Set.of("iceberg_owner", "elastic_viewer"),
          "charlie", Set.of("hive_viewer", "iceberg_viewer")
      );

      @Override
      public Set<String> getGroups(String user) {
        return groups.getOrDefault(user, Collections.emptySet());
      }
    };
  }
}
