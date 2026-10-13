package edu.stankin.cogoalauth.db.repo;

import edu.stankin.cogoalauth.db.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    /** @param email already in lower case */
    Optional<User> findByEmail(String email);

    /** @param email already in lower case */
    boolean existsByEmail(String email);

    boolean existsByUsernameIgnoreCase(String username);

    /**
     * Locks the user row, so concurrent logins of one user update the failed attempts counter one by one.
     *
     * @param email already in lower case
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
