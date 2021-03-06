package org.gbif.registry;

import org.gbif.registry.grizzly.RegistryServer;
import org.gbif.registry.guice.RegistryTestModules;

import java.util.List;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.json.JSONConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Simple test to make sure we can produce the Enumeration response.
 * We use a simple Jersey Client since it's not available in the Java client.
 *
 */
public class EnumerationResourceIT {

 @ClassRule
 public static final RegistryServer registryServer = RegistryServer.INSTANCE;

  private Client publicClient;

  @Before
  public void setupBase() {
    publicClient = buildPublicClient();
  }

  @After
  public void destroyBase() {
    publicClient.destroy();
  }

  private Client buildPublicClient() {
    ClientConfig clientConfig = new DefaultClientConfig();
    clientConfig.getFeatures().put(JSONConfiguration.FEATURE_POJO_MAPPING, Boolean.TRUE);
    return Client.create(clientConfig);
  }

  @Test
  public void testTermEnumeration() {
    ClientResponse res = publicClient.resource(RegistryTestModules.WS_URL)
            .path("enumeration/basic")
            .get(ClientResponse.class);

    List<String> responseContent = res.getEntity(new GenericType<List<String>>(){});
    assertNotNull(responseContent);
    assertTrue(responseContent.size() > 0);
  }
  
}
