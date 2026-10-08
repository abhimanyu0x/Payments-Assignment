package dev.dodo.common;

import java.util.List;

public record Page<T>(List<T> data, String nextCursor) {
}
