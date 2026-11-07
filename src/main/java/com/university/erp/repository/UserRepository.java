package com.university.erp.repository;

import com.university.erp.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Query("""
        select u from User u where
        (:search = '' or lower(u.fullName) like lower(concat('%', :search, '%'))
         or lower(u.email) like lower(concat('%', :search, '%')))
        and (:role is null or u.role = :role)
        and (:status = '' or (:status = 'INACTIVE' and u.isActive = false)
         or (:status = 'PENDING' and u.isActive = true and u.invitationPending = true)
         or (:status = 'ACTIVE' and u.isActive = true and u.invitationPending = false))
        """)
    org.springframework.data.domain.Page<User> searchUsers(
        @org.springframework.data.repository.query.Param("search") String search,
        @org.springframework.data.repository.query.Param("role") User.Role role,
        @org.springframework.data.repository.query.Param("status") String status,
        org.springframework.data.domain.Pageable pageable);
}
