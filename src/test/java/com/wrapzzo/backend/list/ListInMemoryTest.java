package com.wrapzzo.backend.list;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The in-memory list (approval queues): same search, filters, sort and paging as the database one, and refuses what it cannot answer. */
class ListInMemoryTest {

    record Row(String gift, String doctor, int quantity) {
    }

    private final ListInMemory<Row> list = new ListInMemory<Row>()
            .column("gift", Row::gift, true)
            .column("doctor", Row::doctor, true)
            .column("quantity", Row::quantity, false);

    private final List<Row> rows = List.of(
            new Row("Rx Pad", "Dr Asha", 5), new Row("Desk Calendar", "Dr Bose", 20), new Row("Rx Pad", "Dr Chopra", 100), new Row("Cover", "Dr Asha", 7));

    private PageResponse<Row> run(Integer page, Integer size, String search, String sort, String dir, String filters) {
        return list.page(rows, new ListQueryParams(page, size, search, sort, dir, filters).toQuery(), Comparator.comparing(Row::doctor));
    }

    @Test
    void pagesInTheDefaultOrderWithTheTotalOfAllMatches() {
        PageResponse<Row> first = run(0, 3, null, null, null, null);
        assertThat(first.data()).extracting(Row::doctor).containsExactly("Dr Asha", "Dr Asha", "Dr Bose");
        assertThat(first.total()).isEqualTo(4);
        assertThat(first.hasMore()).isTrue();
        assertThat(run(1, 3, null, null, null, null).data()).extracting(Row::doctor).containsExactly("Dr Chopra");
        assertThat(run(9, 3, null, null, null, null).data()).isEmpty();
    }

    @Test
    void searchesOnlyTheSearchableColumnsIgnoringCase() {
        assertThat(run(0, 10, "rx pad", null, null, null).total()).isEqualTo(2);
        assertThat(run(0, 10, "100", null, null, null).total()).isZero(); // quantity is not searched
    }

    @Test
    void filtersByTextAndNumber() {
        assertThat(run(0, 10, null, null, null, "[{\"field\":\"doctor\",\"operator\":\"equals\",\"value\":\"dr asha\"}]").total()).isEqualTo(2);
        assertThat(run(0, 10, null, null, null, "[{\"field\":\"gift\",\"operator\":\"in\",\"value\":[\"cover\",\"desk calendar\"]}]").total()).isEqualTo(2);
        assertThat(run(0, 10, null, null, null, "[{\"field\":\"quantity\",\"operator\":\"between\",\"value\":[6,50]}]").total()).isEqualTo(2);
        assertThat(run(0, 10, null, null, null, "[{\"field\":\"quantity\",\"operator\":\"gte\",\"value\":20}]").total()).isEqualTo(2);
    }

    @Test
    void sortsByAColumnEitherWay() {
        assertThat(run(0, 10, null, "quantity", "desc", null).data()).extracting(Row::quantity).containsExactly(100, 20, 7, 5);
        assertThat(run(0, 10, null, "gift", "asc", null).data().get(0).gift()).isEqualTo("Cover");
    }

    @Test
    void refusesAColumnItDoesNotHave() {
        assertThatThrownBy(() -> run(0, 10, null, "secret", "asc", null)).isInstanceOf(InvalidListQueryException.class).hasMessageContaining("gift, doctor, quantity");
        assertThatThrownBy(() -> run(0, 10, null, null, null, "[{\"field\":\"secret\",\"operator\":\"equals\",\"value\":\"x\"}]")).isInstanceOf(InvalidListQueryException.class);
    }
}
