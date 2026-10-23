package de.dahaem.shared;

/**
 * Which page of a list a caller wants: {@code page} counts from 1, {@code pageSize} is 1 - {@value #MAX_PAGE_SIZE}.
 * Missing values get the documented defaults; out-of-range values are an IllegalArgumentException (the API answers
 * 400) rather than being silently clamped.
 */
public record PageRequest(int page, int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 24;
    public static final int MAX_PAGE_SIZE = 100;

    public PageRequest {
        if (page < 1) {
            throw new IllegalArgumentException("'page' must be 1 or more (the first page is 1)");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("'pageSize' must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    public static PageRequest of(Integer page, Integer pageSize) {
        return new PageRequest(page == null ? 1 : page, pageSize == null ? DEFAULT_PAGE_SIZE : pageSize);
    }

    /** Number of rows before this page. */
    public long offset() {
        return (long) (page - 1) * pageSize;
    }

    /** Pages needed for {@code total} rows (0 for none). */
    public int totalPages(long total) {
        return (int) ((total + pageSize - 1) / pageSize);
    }
}
