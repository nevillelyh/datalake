package io.datalake.trino;

import io.trino.spi.Plugin;
import io.trino.spi.security.GroupProviderFactory;
import io.trino.spi.security.PasswordAuthenticatorFactory;

import java.util.List;

public class DatalakePlugin implements Plugin {
  @Override
  public Iterable<GroupProviderFactory> getGroupProviderFactories() {
    return List.of(new DatalakeGroupProviderFactory());
  }

  @Override
  public Iterable<PasswordAuthenticatorFactory> getPasswordAuthenticatorFactories() {
    return List.of(new DatalakePasswordAuthenticatorFactory());
  }
}
