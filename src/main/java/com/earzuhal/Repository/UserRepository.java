package com.earzuhal.Repository;

import com.earzuhal.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

//@RepositoryRestResource is used to expose the repo
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findById(long id);


    @Query("SELECT s FROM User s ORDER BY s.id ASC")
    List<User> findAllOrderByUserId();
}
