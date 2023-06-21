package ir.co.sadad.noticeapiconsumer.repositories;

import ir.co.sadad.noticeapiconsumer.models.Notification;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface NotificationRepository extends ReactiveMongoRepository<Notification, String> {

//    Mono<Notification> findByDateAndType(Long date, String type);

    Mono<Notification> findByCreationDate(Long creationDate);
}
