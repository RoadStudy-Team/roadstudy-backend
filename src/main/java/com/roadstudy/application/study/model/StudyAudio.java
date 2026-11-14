package com.roadstudy.application.study.model;

import java.nio.file.Path;
import java.util.UUID;

public record StudyAudio(UUID audioId, Path path) {
}
