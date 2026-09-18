package com.splitpay.group;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface GroupRepository extends JpaRepository<SharedGroup,UUID> {
 @Query("select distinct g from SharedGroup g join g.memberIds m where m = :userId order by g.updatedAt desc")
 List<SharedGroup> forUser(@Param("userId") UUID userId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select g from SharedGroup g where g.id = :id")
 Optional<SharedGroup> locked(@Param("id") UUID id);
}
