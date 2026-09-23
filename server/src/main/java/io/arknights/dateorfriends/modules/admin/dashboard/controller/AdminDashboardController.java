package io.arknights.dateorfriends.modules.admin.dashboard.controller;

import io.arknights.dateorfriends.modules.admin.ban.mapper.BanRecordMapper;
import io.arknights.dateorfriends.modules.admin.notice.mapper.NoticeMapper;
import io.arknights.dateorfriends.modules.admin.questionnaire.mapper.QuestionnaireMapper;
import io.arknights.dateorfriends.modules.admin.spine.mapper.SpineAssetMapper;
import io.arknights.dateorfriends.modules.user.auth.mapper.UserMapper;
import io.arknights.dateorfriends.modules.user.online.mapper.OnlineRoomMapper;
import io.arknights.dateorfriends.modules.user.online.race.mapper.RaceMapper;
import io.arknights.dateorfriends.tools.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

    private final UserMapper userMapper;
    private final BanRecordMapper banRecordMapper;
    private final OnlineRoomMapper onlineRoomMapper;
    private final NoticeMapper noticeMapper;
    private final QuestionnaireMapper questionnaireMapper;
    private final SpineAssetMapper spineAssetMapper;
    private final RaceMapper raceMapper;

    public AdminDashboardController(
            UserMapper userMapper,
            BanRecordMapper banRecordMapper,
            OnlineRoomMapper onlineRoomMapper,
            NoticeMapper noticeMapper,
            QuestionnaireMapper questionnaireMapper,
            SpineAssetMapper spineAssetMapper,
            RaceMapper raceMapper
    ) {
        this.userMapper = userMapper;
        this.banRecordMapper = banRecordMapper;
        this.onlineRoomMapper = onlineRoomMapper;
        this.noticeMapper = noticeMapper;
        this.questionnaireMapper = questionnaireMapper;
        this.spineAssetMapper = spineAssetMapper;
        this.raceMapper = raceMapper;
    }

    public record DashboardStats(
            long totalUsers,
            long adminUsers,
            long superAdminUsers,
            long totalBans,
            long activeBans,
            long totalRooms,
            long onlineRooms,
            long totalNotices,
            long publishedNotices,
            long totalQuestionnaires,
            long readyQuestionnaires,
            long totalSpineAssets,
            long totalRaces,
            long activeRaces
    ) {}

    @GetMapping("/stats")
    public Mono<ApiResponse<DashboardStats>> stats() {
        return Mono.fromCallable(() -> {
                    var stats = new DashboardStats(
                            userMapper.countForAdmin(null, null, null, null, null, null, null, 0),
                            userMapper.countAdmin(),
                            userMapper.countSuperAdmin(),
                            banRecordMapper.countTotal(),
                            banRecordMapper.countActive(),
                            onlineRoomMapper.countAll(),
                            onlineRoomMapper.countOnline(),
                            noticeMapper.countAll(),
                            noticeMapper.countPublished(),
                            questionnaireMapper.countAll(),
                            questionnaireMapper.countReady(),
                            spineAssetMapper.count(null),
                            raceMapper.countAll(),
                            raceMapper.countActive()
                    );
                    return ApiResponse.ok(stats);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
