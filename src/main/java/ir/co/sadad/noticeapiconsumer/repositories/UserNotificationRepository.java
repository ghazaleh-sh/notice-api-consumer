package ir.co.sadad.noticeapiconsumer.repositories;

import ir.co.sadad.noticeapiconsumer.models.UserNotification;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface UserNotificationRepository extends ReactiveMongoRepository<UserNotification, String> {

    Mono<UserNotification> findBySsn(String ssn);

}
