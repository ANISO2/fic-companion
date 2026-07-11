package com.fih.companion.roles;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AppUserModeleRepository extends JpaRepository<AppUserModele, AppUserModele.Id> {

    @Query("SELECT m.id.modelebilletId FROM AppUserModele m WHERE m.id.appUserId = :userId")
    List<Integer> findModelIdsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM AppUserModele m WHERE m.id.appUserId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
