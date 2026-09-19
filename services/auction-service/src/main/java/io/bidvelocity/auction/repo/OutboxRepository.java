package io.bidvelocity.auction.repo;

import io.bidvelocity.auction.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findByTopicAndIdGreaterThanOrderByIdAsc(String topic, long afterId);

    @Modifying
    @Query("UPDATE OutboxEvent e SET e.published = true WHERE e.id IN :ids")
    int markPublished(List<Long> ids);
}
