package com.fiscuvia;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.fiscuvia.platform.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ApiPrimitivesTests {
    @Test void pagingDefaultsBoundsAndStableSort() {
        var page = PageQuery.parse(null, null, null, Set.of("id"));
        assertThat(page.size()).isEqualTo(20);
        assertThat(page.page()).isZero();
        assertThat(page.sort()).isEqualTo("id");
        assertThat(PageQuery.parse(10000, 100, "id,desc", Set.of("id")).offset()).isEqualTo(1000000);
        assertThatThrownBy(() -> PageQuery.parse(0, 1, "privateData,asc", Set.of("id"))).isInstanceOf(ApiFailure.class);
    }

    @Test void idempotencyOnlyParsesCanonicalUuidWithoutClaimingDurableReplay() {
        String uuid = "00000000-0000-4000-8000-000000000001";
        assertThat(ApiIdentifiers.idempotencyKey(uuid).toString()).isEqualTo(uuid);
        for (String invalid : List.of("", "1-1-1-1-1", "not-a-uuid", " " + uuid)) {
            assertThatThrownBy(() -> ApiIdentifiers.idempotencyKey(invalid)).isInstanceOf(ApiFailure.class);
        }
    }

    @Test void pageResponseCopiesItemsAndRejectsOverflow() {
        var original = new ArrayList<>(List.of("DEMO"));
        var response = new PageResponse<>(original, 0, 1, false);
        original.clear();
        assertThat(response.items()).containsExactly("DEMO");
        assertThatThrownBy(() -> new PageResponse<>(List.of("a", "b"), 0, 1, false)).isInstanceOf(IllegalArgumentException.class);
    }
}
