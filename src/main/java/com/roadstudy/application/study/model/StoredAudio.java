package com.roadstudy.application.study.model;

import java.nio.file.Path;
import java.util.UUID;

public record StoredAudio(UUID audioId, Path path) {
}
