package com.mailorbit.repository;

import com.mailorbit.entity.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {

    List<ImportBatch> findAllByOrderByIdDesc();
}
