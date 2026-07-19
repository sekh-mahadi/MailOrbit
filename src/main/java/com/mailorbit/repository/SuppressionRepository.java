package com.mailorbit.repository;

import com.mailorbit.entity.SuppressionEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SuppressionRepository extends JpaRepository<SuppressionEntry, Long> {

    Optional<SuppressionEntry> findByPattern(String pattern);

    List<SuppressionEntry> findAllByOrderByIdDesc();
}
