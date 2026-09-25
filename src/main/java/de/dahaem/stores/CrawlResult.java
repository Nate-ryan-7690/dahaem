package de.dahaem.stores;

import java.util.List;

/**
 * Result of crawling a chain's full range. failedPages / truncatedPages tell the
 * comparator whether the pull was complete enough to trust missing products as removed.
 */
public record CrawlResult(List<StoreProduct> products, int pagesCrawled,
                          List<String> failedPages, List<String> truncatedPages) {

    public boolean isComplete() {
        return failedPages.isEmpty() && truncatedPages.isEmpty();
    }
}
