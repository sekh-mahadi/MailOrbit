package com.mailorbit.repository;

import com.mailorbit.entity.ContactStatus;
import com.mailorbit.entity.EmailContact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailContactRepository extends JpaRepository<EmailContact, Long> {

    boolean existsByDedupeKey(String dedupeKey);

    Page<EmailContact> findByStatus(ContactStatus status, Pageable pageable);

    Page<EmailContact> findByEmailContainingIgnoreCase(String q, Pageable pageable);

    Page<EmailContact> findByStatusAndEmailContainingIgnoreCase(ContactStatus status, String q, Pageable pageable);

    List<EmailContact> findByStatus(ContactStatus status);

    List<EmailContact> findByStatusAndBatchId(ContactStatus status, Long batchId);

    List<EmailContact> findByBatchId(Long batchId);

    long countByStatus(ContactStatus status);

    @Query("select c.domain, count(c) from EmailContact c where c.domain is not null and c.domain <> '' " +
           "group by c.domain order by count(c) desc, c.domain asc")
    List<Object[]> topDomains(Pageable pageable);

    @Query("select avg(c.score) from EmailContact c where c.score is not null")
    Double averageScore();
}
