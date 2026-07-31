package com.gridveritas.core.repository;

import com.gridveritas.core.domain.Anchor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnchorRepository extends JpaRepository<Anchor, UUID> {

    Optional<Anchor> findFirstByRootIdOrderByAnchoredAtAsc(UUID rootId);

    boolean existsByRootId(UUID rootId);
}
