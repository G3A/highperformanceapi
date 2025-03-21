package co.g3a.highperformanceapi.features.tasks;

import java.util.UUID;

public class TaskResponse {
        private final UUID taskId;
        private final String status;

        public TaskResponse(UUID taskId, String status) {
            this.taskId = taskId;
            this.status = status;
        }

        public UUID getTaskId() {
            return taskId;
        }

        public String getStatus() {
            return status;
        }
    }