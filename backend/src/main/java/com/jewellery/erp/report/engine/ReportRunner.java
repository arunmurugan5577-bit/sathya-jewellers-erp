package com.jewellery.erp.report.engine;

import com.jewellery.erp.common.config.BusinessClock;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs a sheet's query and hands rows, one at a time, to a sink.
 *
 * <p>Rows are streamed with a JDBC fetch size rather than loaded into a list, so a
 * year of invoices never sits in memory at once. PostgreSQL honours the fetch size
 * only inside a transaction, which is why callers run reports in a read-only one.
 */
@Component
public class ReportRunner {

    private static final int FETCH_SIZE = 500;

    private final NamedParameterJdbcTemplate jdbc;
    private final BusinessClock businessClock;

    public ReportRunner(DataSource dataSource, BusinessClock businessClock) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setFetchSize(FETCH_SIZE);
        this.jdbc = new NamedParameterJdbcTemplate(template);
        this.businessClock = businessClock;
    }

    @FunctionalInterface
    public interface RowSink {
        void accept(Object[] values);
    }

    public void run(ReportDefinition.Sheet sheet, RowSink sink) {
        List<ReportColumn> columns = sheet.columns();
        jdbc.query(sheet.sql(), sheet.parameters(), (RowCallbackHandler) rs -> {
            Object[] values = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                values[i] = read(rs, i + 1, columns.get(i).type());
            }
            sink.accept(values);
        });
    }

    private Object read(ResultSet rs, int index, ReportColumn.Type type) throws SQLException {
        return switch (type) {
            case TEXT -> rs.getString(index);
            case DATE -> rs.getObject(index, LocalDate.class);
            case DATETIME -> {
                OffsetDateTime value = rs.getObject(index, OffsetDateTime.class);
                yield value == null ? null : value.atZoneSameInstant(businessClock.zone()).toLocalDateTime();
            }
            case INTEGER -> {
                long value = rs.getLong(index);
                yield rs.wasNull() ? null : value;
            }
            case WEIGHT, MONEY, PERCENT -> {
                BigDecimal value = rs.getBigDecimal(index);
                yield value;
            }
        };
    }
}
