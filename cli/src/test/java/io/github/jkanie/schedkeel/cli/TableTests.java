package io.github.jkanie.schedkeel.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TableTests {

    @Test
    void padsEachColumnToItsWidestCell() {
        String out = new Table("NAME", "ON")
                .row("nightly-report", true)
                .row("a", null)
                .render("none");
        assertThat(out).isEqualTo("""
                NAME            ON
                nightly-report  true
                a               -""");
    }

    @Test
    void printsTheEmptyMessageWhenThereAreNoRows() {
        assertThat(new Table("A").render("nothing here")).isEqualTo("nothing here");
    }

    @Test
    void writesCsvWithQuotesOnlyWhereACellNeedsThem() {
        String out = new Table("NAME", "CRON", "PAYLOAD")
                .row("ping", "0 * * * * *", null)
                .row("say \"hi\"", "0 0 2 * * *", "a,b\nc")
                .row("-", "", "x")
                .csv();
        assertThat(out)
                .isEqualTo("NAME,CRON,PAYLOAD\n"
                        + "ping,0 * * * * *,\n"
                        + "\"say \"\"hi\"\"\",0 0 2 * * *,\"a,b\nc\"\n"
                        + "-,,x");
    }

    @Test
    void writesOnlyTheCsvHeaderWhenThereAreNoRows() {
        assertThat(new Table("ID", "JOB").csv()).isEqualTo("ID,JOB");
    }

    @Test
    void refusesARowOfTheWrongWidth() {
        assertThatThrownBy(() -> new Table("A", "B").row("only one")).isInstanceOf(IllegalArgumentException.class);
    }
}
