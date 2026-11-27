package com.eclectics.worksync.users.repository;

import com.eclectics.worksync.users.entity.OrganizationPermission;
import com.eclectics.worksync.users.entity.UserPermission;
import java.util.Set;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Users-only persistence. Scalar reads bypass stale managed grant entities and no permission cache is used. */
public interface UserPermissionRepository extends JpaRepository<UserPermission, UUID> {
    @Query("select g.permission from UserPermission g where g.userId = :userId")
    Set<OrganizationPermission> findPermissionsByUserId(@Param("userId") UUID userId);

    /** Scalar projection for safe response mapping; intentionally restricted to the current page. */
    interface PermissionGrantView {
        UUID getUserId();
        OrganizationPermission getPermission();
    }

    @Query("select g.userId as userId, g.permission as permission from UserPermission g where g.userId in :userIds")
    List<PermissionGrantView> findPermissionsForUsers(@Param("userIds") Collection<UUID> userIds);

    @Modifying
    @Query("delete from UserPermission g where g.userId = :userId and g.permission = :permission")
    int deleteGrant(@Param("userId") UUID userId, @Param("permission") OrganizationPermission permission);
}
