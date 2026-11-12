package com.roadstudy.application.study;

import java.nio.file.Path;
import java.util.UUID;

public record StudyAudio(UUID audioId, Path path) {
}
