package uk.ac.dundee.ga.mms.storage;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResult<T> of(List<T> all, int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 500);
        int from = Math.min(p * s, all.size());
        int to = Math.min(from + s, all.size());
        int pages = (int) Math.ceil(all.size() / (double) s);
        return new PageResult<>(all.subList(from, to), p, s, all.size(), pages);
    }

    public <R> PageResult<R> map(Function<T, R> f) {
        return new PageResult<>(content.stream().map(f).toList(), page, size, totalElements, totalPages);
    }
}
