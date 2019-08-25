package dev.tycho.stonks.Database;

import com.j256.ormlite.dao.BaseDaoImpl;
import com.j256.ormlite.support.ConnectionSource;

import java.sql.SQLException;

public class HoldingDaoImpl extends BaseDaoImpl<Holding, Integer> implements HoldingDao {
    public HoldingDaoImpl(ConnectionSource connectionSource) throws SQLException {
        super(connectionSource, Holding.class);
    }

    @Override
    public boolean memberHasHoldings(Member member) {
        return false;
    }
}
