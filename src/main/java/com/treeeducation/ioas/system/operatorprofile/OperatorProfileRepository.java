package com.treeeducation.ioas.system.operatorprofile;
import org.springframework.data.jpa.repository.JpaRepository;import java.util.List;
public interface OperatorProfileRepository extends JpaRepository<OperatorProfile,Long>{ List<OperatorProfile> findByEnabledTrueOrderByNameAsc(); }
