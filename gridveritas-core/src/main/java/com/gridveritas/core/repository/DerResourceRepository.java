package com.gridveritas.core.repository;

import com.gridveritas.core.domain.DerResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DerResourceRepository extends JpaRepository<DerResource, UUID> {

    List<DerResource> findAllByOrderByCreatedAtAsc();
}
