package us.shandian.giga.get;

import androidx.annotation.NonNull;

public class FinishedMission extends Mission {

    public FinishedMission() {
    }

    public FinishedMission(@NonNull DownloadMission mission) {
        source = mission.source;
        thumbnailUrl = mission.thumbnailUrl;
        length = mission.length;
        timestamp = mission.timestamp;
        kind = mission.kind;
        storage = mission.storage;
    }

}
