package ir.co.sadad.noticeapiconsumer.service;

import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import ir.co.sadad.noticeapiconsumer.dtos.TransactionNoticeReqDto;
import ir.co.sadad.noticeapiconsumer.enums.NoticeType;
import ir.co.sadad.noticeapiconsumer.models.Notification;
import ir.co.sadad.noticeapiconsumer.models.UserNotification;
import ir.co.sadad.noticeapiconsumer.repositories.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * a service as reactiveKafkaSource
 *
 * @author g.shahrokhabadi
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ReactiveConsumerService {

//    private final Scheduler scheduler = Schedulers.newSingle("sample", true);

    private final UserNotificationRepository userNotificationRepository;


    public Flux<String> storeInDB(TransactionNoticeReqDto singleNoticeReqDto) {
        log.info("Successfully processed singleNoticeReqDto with title {} from Kafka", singleNoticeReqDto.getDate());

        List<Notification> notice = new ArrayList<>();
        notice.add(Notification.builder()
                .creationDate(System.currentTimeMillis())
                .account(singleNoticeReqDto.getAccount())
                .balance(singleNoticeReqDto.getBalance())
                .withdraw(singleNoticeReqDto.getWithdraw())
                .date(singleNoticeReqDto.getDate())
                .bankName(singleNoticeReqDto.getBankName())
                .transactionType(singleNoticeReqDto.getTransactionType())
                .type(NoticeType.TRANSACTION.getValue())
                .build());

        return Flux
                .just(singleNoticeReqDto)
                .flatMap(currentDto ->
                        userNotificationRepository.findBySsn(currentDto.getSsn())
                                .flatMap(userNotif -> {
                                    List<Notification> notifsOfUser = userNotif != null ? userNotif.getNotificationTransactions() : new ArrayList<>();
                                    if (notifsOfUser == null) notifsOfUser = new ArrayList<>();
                                    notifsOfUser.add(notice.get(0));
                                    assert userNotif != null;
                                    userNotif.setNotificationTransactions(notifsOfUser);
                                    userNotif.setRemainNotificationCount(userNotif.getRemainNotificationCount() + 1);
                                    userNotif.setNotificationCount(userNotif.getNotificationCount() + 1);
                                    return userNotificationRepository.save(userNotif)
                                            .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                                                    .filter(this::isConnectionIssue)
                                                    .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> retrySignal.failure()))
                                            .onErrorResume(throwable -> {
                                                log.error("Failed to save user notification: {}", throwable.getMessage());
                                                return Mono.empty();
                                            });

                                })
                                .switchIfEmpty(Mono.defer(() -> userNotificationRepository.insert(UserNotification
                                        .builder()
                                        .ssn(currentDto.getSsn())
                                        .notificationTransactions(notice)
                                        .notificationCampaignsCreateDate(null)
                                        .lastSeenCampaign(0L)
                                        .lastSeenTransaction(0L)
                                        .remainNotificationCount(1)
                                        .notificationCount(1)
                                        .build())))

                                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                                        .filter(this::isConnectionIssue)
                                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> retrySignal.failure()))
                                .onErrorResume(throwable -> {
                                    log.error("Failed to retrieve or insert user notification: {}", throwable.getMessage());
                                    return Mono.empty();
                                })

                                .doOnSuccess(result -> System.out.print("consumer Message was saved in MongoDB with id: " + result.getId()))
                                .map(UserNotification::getId));

    }


    private boolean isConnectionIssue(Throwable throwable) {
        // Connection-related exceptions in MongoDB
        return throwable instanceof MongoTimeoutException ||
                throwable instanceof MongoSocketException;
    }

}