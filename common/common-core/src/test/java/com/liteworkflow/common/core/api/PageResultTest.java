package com.liteworkflow.common.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Test
    void calculatesPagesAndDefensivelyCopiesRecords() {
        List<String> source = new ArrayList<>(List.of("one", "two"));

        PageResult<String> result = PageResult.of(source, 21, 1, 10);
        source.clear();

        assertThat(result.pages()).isEqualTo(3);
        assertThat(result.records()).containsExactly("one", "two");
        assertThatThrownBy(() -> result.records().add("three"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidPagingArguments() {
        assertThatThrownBy(() -> PageResult.of(List.of(), 0, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageResult.of(List.of(), 0, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
