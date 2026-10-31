package com.wrapzzo.backend.dto;

import java.util.List;

public record IdListResponse<T>(List<T> data) {
}
