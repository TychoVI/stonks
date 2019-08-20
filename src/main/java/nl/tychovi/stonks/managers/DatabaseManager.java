package nl.tychovi.stonks.managers;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.logger.LocalLog;
import com.j256.ormlite.table.TableUtils;
import nl.tychovi.stonks.Database.*;
import nl.tychovi.stonks.Stonks;
import nl.tychovi.stonks.command.CommandCompany;

import java.io.IOException;
import java.sql.SQLException;

public class DatabaseManager extends SpigotModule {

    public DatabaseManager(Stonks plugin) {
        super("databaseManager", plugin);
    }
    private JdbcConnectionSource connectionSource = null;

    private CompanyDao companyDao = null;
    private MemberDao memberDao = null;
    private CompanyAccountDao companyAccountDao = null;

    @Override
    public void enable() {
        synchronized (this) {
            System.setProperty(LocalLog.LOCAL_LOG_LEVEL_PROPERTY, "ERROR");

            String host = plugin.getConfig().getString("mysql.host");
            String port = plugin.getConfig().getString("mysql.port");
            String database = plugin.getConfig().getString("mysql.database");
            String username = plugin.getConfig().getString("mysql.username");
            String password = plugin.getConfig().getString("mysql.password");
            String useSsl = plugin.getConfig().getString("mysql.ssl");

            String databaseUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=GMT&useSSL=" + useSsl;

            try {
                connectionSource = new JdbcConnectionSource(databaseUrl, username, password);
                companyDao = DaoManager.createDao(connectionSource, Company.class);
                memberDao = DaoManager.createDao(connectionSource, Member.class);
                companyAccountDao = DaoManager.createDao(connectionSource, CompanyAccount.class);
                TableUtils.createTableIfNotExists(connectionSource, Company.class);
                TableUtils.createTableIfNotExists(connectionSource, Member.class);
                TableUtils.createTableIfNotExists(connectionSource, CompanyAccount.class);

                Stonks.companies.addAll(companyDao.queryForAll());
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void addCommands() {
        addCommand("company", new CommandCompany(this, plugin));
    }

    @Override
    public void disable() {
        try {
            connectionSource.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public CompanyDao getCompanyDao() {
        return companyDao;
    }

    public MemberDao getMemberDao() {
        return memberDao;
    }

    public CompanyAccountDao getCompanyAccountDao() { return companyAccountDao; }
}
