package com.kross.integration;

import com.kross.integration.entity.IntegrationGrant;
import com.kross.integration.entity.IntegrationInstallation;
import com.kross.integration.entity.IntegrationOAuthState;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IntegrationMapper {
  List<IntegrationInstallation> listInstallations(@Param("organizationId") String organizationId);

  Optional<IntegrationInstallation> findInstallation(@Param("id") String id);

  Optional<IntegrationInstallation> findInstallationByCatalog(
      @Param("organizationId") String organizationId, @Param("catalogId") String catalogId);

  void insertInstallation(IntegrationInstallation row);

  int updateInstallation(IntegrationInstallation row);

  int deleteInstallation(@Param("id") String id);

  Optional<IntegrationGrant> findGrant(
      @Param("installationId") String installationId, @Param("userId") String userId);

  List<IntegrationGrant> listActiveGrants(
      @Param("organizationId") String organizationId, @Param("userId") String userId);

  void insertGrant(IntegrationGrant row);

  int updateGrant(IntegrationGrant row);

  int deleteGrant(@Param("id") String id);

  int deleteGrantsByInstallation(@Param("installationId") String installationId);

  void insertOAuthState(IntegrationOAuthState row);

  Optional<IntegrationOAuthState> findOAuthState(@Param("id") String id);

  int deleteOAuthState(@Param("id") String id);

  int deleteExpiredOAuthStates();
}