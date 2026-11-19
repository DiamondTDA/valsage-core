package io.github.xylars.valsage.repo;

import io.github.xylars.valsage.entity.MyUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MyUserRepo extends JpaRepository<MyUser,Long> {
    Optional<MyUser> findByEmail(String email);

    boolean existsByEmail(String email);

    List<MyUser> findAllByOrderByCreatedAtDesc();
}
