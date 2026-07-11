package com.fih.companion.roles;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** Recherche insensible à la casse, comme le repli invitations-accounts. */
    @Query("SELECT u FROM AppUser u WHERE lower(u.username) = lower(:username)")
    Optional<AppUser> findByUsernameIgnoreCase(@Param("username") String username);
}
