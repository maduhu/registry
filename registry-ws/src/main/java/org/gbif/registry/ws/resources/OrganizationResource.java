/*
 * Copyright 2013 Global Biodiversity Information Facility (GBIF)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gbif.registry.ws.resources;

import org.gbif.api.model.common.paging.Pageable;
import org.gbif.api.model.common.paging.PagingResponse;
import org.gbif.api.model.registry.Dataset;
import org.gbif.api.model.registry.Installation;
import org.gbif.api.model.registry.Organization;
import org.gbif.api.service.registry.OrganizationService;
import org.gbif.api.vocabulary.Country;
import org.gbif.api.vocabulary.IdentifierType;
import org.gbif.registry.persistence.mapper.CommentMapper;
import org.gbif.registry.persistence.mapper.ContactMapper;
import org.gbif.registry.persistence.mapper.DatasetMapper;
import org.gbif.registry.persistence.mapper.EndpointMapper;
import org.gbif.registry.persistence.mapper.IdentifierMapper;
import org.gbif.registry.persistence.mapper.InstallationMapper;
import org.gbif.registry.persistence.mapper.MachineTagMapper;
import org.gbif.registry.persistence.mapper.OrganizationMapper;
import org.gbif.registry.persistence.mapper.TagMapper;
import org.gbif.registry.ws.security.EditorAuthorizationService;

import java.util.Random;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.eventbus.EventBus;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import static org.gbif.registry.ws.security.UserRoles.ADMIN_ROLE;
import static org.gbif.registry.ws.security.UserRoles.EDITOR_ROLE;

/**
 * A MyBATIS implementation of the service.
 */
@Path("organization")
@Singleton
public class OrganizationResource extends BaseNetworkEntityResource<Organization> implements OrganizationService {

  protected static final int MINIMUM_PASSWORD_SIZE = 6;
  protected static final int MAXIMUM_PASSWORD_SIZE = 15;
  private final static String PASSWORD_ALLOWED_CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
  private final DatasetMapper datasetMapper;
  private final OrganizationMapper organizationMapper;
  private final InstallationMapper installationMapper;

  @Inject
  public OrganizationResource(
    OrganizationMapper organizationMapper,
    ContactMapper contactMapper,
    EndpointMapper endpointMapper,
    MachineTagMapper machineTagMapper,
    TagMapper tagMapper,
    IdentifierMapper identifierMapper,
    CommentMapper commentMapper,
    DatasetMapper datasetMapper,
    InstallationMapper installationMapper,
    EventBus eventBus,
    EditorAuthorizationService userAuthService) {
    super(organizationMapper,
      commentMapper,
      contactMapper,
      endpointMapper,
      identifierMapper,
      machineTagMapper,
      tagMapper,
      Organization.class,
      eventBus,
      userAuthService);
    this.datasetMapper = datasetMapper;
    this.organizationMapper = organizationMapper;
    this.installationMapper = installationMapper;
  }

  /**
   * This method overrides the create method for an organization, populating the password field with a randomly
   * generated string before passing on to the superclass create method.
   *
   * @param organization organization
   * @param security     SecurityContext (security related information)
   *
   * @return key of entity created
   */
  @POST
  @RolesAllowed({ADMIN_ROLE, EDITOR_ROLE})
  @Override
  public UUID create(@NotNull Organization organization, @Context SecurityContext security) {
    organization.setPassword(generatePassword());
    return super.create(organization, security);
  }

  /**
   * All network entities support simple (!) search with "&q=".
   * This is to support the console user interface, and is in addition to any complex, faceted search that might
   * additionally be supported, such as dataset search.
   * Organizations can also be filtered by their country, but a search and country filter cannot be combined.
   */
  @GET
  public PagingResponse<Organization> list(@Nullable @Context Country country,
    @Nullable @QueryParam("identifierType") IdentifierType identifierType,
    @Nullable @QueryParam("identifier") String identifier,
    @Nullable @QueryParam("q") String query,
    @Nullable @Context Pageable page) {
    // This is getting messy: http://dev.gbif.org/issues/browse/REG-426
    if (country != null) {
      return listByCountry(country, page);
    } else if (identifierType != null && identifier != null) {
      return listByIdentifier(identifierType, identifier, page);
    } else if (identifier != null) {
      return listByIdentifier(identifier, page);
    } else if (!Strings.isNullOrEmpty(query)) {
      return search(query, page);
    } else {
      return list(page);
    }
  }

  @GET
  @Path("{key}/hostedDataset")
  @Override
  public PagingResponse<Dataset> hostedDatasets(@PathParam("key") UUID organizationKey, @Context Pageable page) {
    return pagingResponse(page, datasetMapper.countDatasetsHostedBy(organizationKey),
      datasetMapper.listDatasetsHostedBy(organizationKey, page));
  }

  @GET
  @Path("{key}/publishedDataset")
  @Override
  public PagingResponse<Dataset> publishedDatasets(@PathParam("key") UUID organizationKey, @Context Pageable page) {
    return pagingResponse(page, datasetMapper.countDatasetsPublishedBy(organizationKey),
      datasetMapper.listDatasetsPublishedBy(organizationKey, page));
  }

  /**
   * This is an HTTP only method to provide the count for the homepage of the portal. The homepage count excludes
   * non publishing an non endorsed datasets.
   */
  @GET
  @Path("count")
  public int countOrganizations() {
    return organizationMapper.countPublishing();
  }

  @Override
  public PagingResponse<Organization> listByCountry(Country country, @Nullable Pageable page) {
    return pagingResponse(page, organizationMapper.countOrganizationsByCountry(country),
      organizationMapper.organizationsByCountry(country, page));
  }

  @GET
  @Path("{key}/installation")
  @Override
  public PagingResponse<Installation> installations(@PathParam("key") UUID organizationKey, @Context Pageable page) {
    return pagingResponse(page, installationMapper.countInstallationsByOrganization(organizationKey),
      installationMapper.listInstallationsByOrganization(organizationKey, page));
  }

  @GET
  @Path("deleted")
  @Override
  public PagingResponse<Organization> listDeleted(@Context Pageable page) {
    return pagingResponse(page, organizationMapper.countDeleted(), organizationMapper.deleted(page));
  }

  @GET
  @Path("pending")
  @Override
  public PagingResponse<Organization> listPendingEndorsement(@Context Pageable page) {
    return pagingResponse(page, organizationMapper.countPendingEndorsements(null),
      organizationMapper.pendingEndorsements(null, page));
  }

  @GET
  @Path("nonPublishing")
  @Override
  public PagingResponse<Organization> listNonPublishing(@Context Pageable page) {
    return pagingResponse(page, organizationMapper.countNonPublishing(), organizationMapper.nonPublishing(page));
  }

  /**
   * This is an HTTP only method to retrieve the password for an organization.
   *
   * @param organizationKey organization key
   *
   * @return password if set, warning message if not set, or null if organization doesn't exist
   */
  @Path("{key}/password")
  @GET
  @RolesAllowed(ADMIN_ROLE)
  @Produces(MediaType.TEXT_PLAIN)
  public String retrievePassword(@PathParam("key") UUID organizationKey) {
    Organization o = get(organizationKey);
    if (o == null) {
      return null;
    }
    // Organization.password is never null according to database schema. API doesn't mirror this though.
    return o.getPassword();
  }

  /**
   * Randomly generates a password for an organization.
   *
   * @return generated password
   */
  @VisibleForTesting
  protected static String generatePassword() {
    Random random = new Random();
    // randomly calculate the size of the password, between 0 and MAXIMUM_PASSWORD_SIZE
    int size = random.nextInt(MAXIMUM_PASSWORD_SIZE);
    // ensure the size is at least greater than or equal to MINIMUM_PASSWORD_SIZE
    size = (size < MINIMUM_PASSWORD_SIZE) ? MINIMUM_PASSWORD_SIZE : size;

    // generate the password
    StringBuilder password = new StringBuilder();
    int randomIndex;
    while (size-- > 0) {
      random = new Random();
      randomIndex = random.nextInt(PASSWORD_ALLOWED_CHARACTERS.length());
      password.append(PASSWORD_ALLOWED_CHARACTERS.charAt(randomIndex));
    }

    return password.toString();
  }
}
