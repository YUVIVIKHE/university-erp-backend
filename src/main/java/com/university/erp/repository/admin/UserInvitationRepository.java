package com.university.erp.repository.admin;
import com.university.erp.entity.admin.UserInvitation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface UserInvitationRepository extends JpaRepository<UserInvitation, Long> {
    Optional<UserInvitation> findByUserId(Long userId);
    Optional<UserInvitation> findByTokenHash(String tokenHash);
    @Query("select i.user.id from UserInvitation i where i.tokenHash = :hash")
    Optional<Long> userIdForToken(@Param("hash") String hash);
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from UserInvitation i where i.tokenHash = :hash")
    Optional<UserInvitation> lockByTokenHash(@Param("hash") String hash);
}
