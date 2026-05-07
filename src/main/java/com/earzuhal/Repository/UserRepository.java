package com.earzuhal.Repository;

import com.earzuhal.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


//@RepositoryRestResource is used to expose the repo
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findById(long id);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameOrEmail(String username, String email);

    Boolean existsByUsername(String username);

    Boolean existsByEmail(String email);

    Optional<User> findByTcKimlik(String tcKimlik);

    // Soft-delete farkındalı bulucular — login + uniqueness kontrolünde
    // silinmiş kullanıcılar yokmuş gibi davransın.
    @Query("SELECT u FROM User u WHERE u.username = :username AND u.deletedAt IS NULL")
    Optional<User> findActiveByUsername(@org.springframework.data.repository.query.Param("username") String username);

    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findActiveByEmail(@org.springframework.data.repository.query.Param("email") String email);

    @Query("SELECT u FROM User u WHERE (u.username = :id OR u.email = :id) AND u.deletedAt IS NULL")
    Optional<User> findActiveByUsernameOrEmail(@org.springframework.data.repository.query.Param("id") String id);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END FROM User u WHERE u.username = :username AND u.deletedAt IS NULL")
    Boolean existsActiveByUsername(@org.springframework.data.repository.query.Param("username") String username);

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN TRUE ELSE FALSE END FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    Boolean existsActiveByEmail(@org.springframework.data.repository.query.Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.tcKimlik = :tc AND u.deletedAt IS NULL")
    Optional<User> findActiveByTcKimlik(@org.springframework.data.repository.query.Param("tc") String tcKimlik);

    @Query("SELECT s FROM User s ORDER BY s.id ASC")
    List<User> findAllOrderByUserId();
}
