package io.datalake.trino;

import io.trino.spi.security.AccessDeniedException;
import io.trino.spi.security.BasicPrincipal;
import io.trino.spi.security.PasswordAuthenticator;
import io.trino.spi.security.PasswordAuthenticatorFactory;

import java.security.Principal;
import java.util.Map;

public class DatalakePasswordAuthenticatorFactory implements PasswordAuthenticatorFactory {
  @Override
  public String getName() {
    return "datalake";
  }

  @Override
  public PasswordAuthenticator create(Map<String, String> config) {
    return new PasswordAuthenticator() {
      private final Map<String, String> passwords = Map.of(
          "trino", "trinopass",
          "alice", "alicepass",
          "bob", "bobpass",
          "charlie", "charliepass"
      );
      @Override
      public Principal createAuthenticatedPrincipal(String user, String password) {
        String p = passwords.get(user);
        if (p != null && p.equals(password)) {
          return new BasicPrincipal(user);
        } else {
          throw new AccessDeniedException(user);
        }
      }
    };
  }
}
