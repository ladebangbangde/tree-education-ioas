package com.treeeducation.ioas.system.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** User persistence gateway. */
public interface UserRepository extends JpaRepository<User, Long> { Optional<User> findByUsername(String username); }
