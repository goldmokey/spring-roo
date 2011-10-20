package org.springframework.roo.addon.jpa;

import java.util.SortedSet;
import java.util.logging.Logger;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.springframework.roo.addon.propfiles.PropFileOperations;
import org.springframework.roo.project.Path;
import org.springframework.roo.project.PomManagementService;
import org.springframework.roo.shell.CliAvailabilityIndicator;
import org.springframework.roo.shell.CliCommand;
import org.springframework.roo.shell.CliOption;
import org.springframework.roo.shell.CommandMarker;
import org.springframework.roo.shell.converters.StaticFieldConverter;
import org.springframework.roo.support.logging.HandlerUtils;

/**
 * Commands for the JPA add-on to be used by the ROO shell.
 *
 * @author Stefan Schmidt
 * @author Ben Alex
 * @author Alan Stewart
 * @since 1.0
 */
@Component
@Service
public class JpaCommands implements CommandMarker {

	// Constants
	private static Logger logger = HandlerUtils.getLogger(JpaCommands.class);

	// Fields
	@Reference private JpaOperations jpaOperations;
	@Reference private PomManagementService pomManagementService;
	@Reference private PropFileOperations propFileOperations;
	@Reference private StaticFieldConverter staticFieldConverter;

	protected void activate(final ComponentContext context) {
		staticFieldConverter.add(JdbcDatabase.class);
		staticFieldConverter.add(OrmProvider.class);
	}

	protected void deactivate(final ComponentContext context) {
		staticFieldConverter.remove(JdbcDatabase.class);
		staticFieldConverter.remove(OrmProvider.class);
	}

	@CliAvailabilityIndicator({ "jpa setup", "persistence setup" })
	public boolean isInstallJpaAvailable() {
		return jpaOperations.isJpaInstallationPossible() || jpaOperations.isJpaInstalled();
	}

	@CliCommand(value = "jpa setup", help = "Install or updates a JPA persistence provider in your project")
	public void installJpa(
		@CliOption(key = "provider", mandatory = true, help = "The persistence provider to support") final OrmProvider ormProvider,
		@CliOption(key = "database", mandatory = true, help = "The database to support") final JdbcDatabase jdbcDatabase,
		@CliOption(key = "applicationId", mandatory = false, unspecifiedDefaultValue = "the project's name", help = "The Google App Engine application identifier to use") final String applicationId,
		@CliOption(key = "jndiDataSource", mandatory = false, help = "The JNDI datasource to use") final String jndi,
		@CliOption(key = "hostName", mandatory = false, help = "The host name to use") final String hostName,
		@CliOption(key = "databaseName", mandatory = false, help = "The database name to use") final String databaseName,
		@CliOption(key = "userName", mandatory = false, help = "The username to use") final String userName,
		@CliOption(key = "password", mandatory = false, help = "The password to use") final String password,
		@CliOption(key = "transactionManager", mandatory = false, help = "The transaction manager name") final String transactionManager,
		@CliOption(key = "persistenceUnit", mandatory = false, help = "The persistence unit name to be used in the persistence.xml file") final String persistenceUnit) {

		if (jdbcDatabase == JdbcDatabase.GOOGLE_APP_ENGINE && ormProvider != OrmProvider.DATANUCLEUS) {
			logger.warning("Provider must be " + OrmProvider.DATANUCLEUS.name() + " for the Google App Engine");
			return;
		}

		if (jdbcDatabase == JdbcDatabase.DATABASE_DOT_COM && ormProvider != OrmProvider.DATANUCLEUS_2) {
			logger.warning("Provider must be " + OrmProvider.DATANUCLEUS_2.name() + " for Database.com");
			return;
		}

		if (jdbcDatabase == JdbcDatabase.FIREBIRD && !isJdk6OrHigher()) {
			logger.warning("JDK must be 1.6 or higher to use Firebird");
			return;
		}

		jpaOperations.configureJpa(ormProvider, jdbcDatabase, jndi, applicationId, hostName, databaseName, userName, password, transactionManager, persistenceUnit, pomManagementService.getFocusedModuleName());
	}

	@Deprecated
	@CliCommand(value = "persistence setup", help = "Install or updates a JPA persistence provider in your project - deprecated, use 'jpa setup' instead")
	public void installPersistence(
		@CliOption(key = "provider", mandatory = true, help = "The persistence provider to support") final OrmProvider ormProvider,
		@CliOption(key = "database", mandatory = true, help = "The database to support") final JdbcDatabase jdbcDatabase,
		@CliOption(key = "applicationId", mandatory = false, unspecifiedDefaultValue = "the project's name", help = "The Google App Engine application identifier to use") final String applicationId,
		@CliOption(key = "jndiDataSource", mandatory = false, help = "The JNDI datasource to use") final String jndi,
		@CliOption(key = "hostName", mandatory = false, help = "The host name to use") final String hostName,
		@CliOption(key = "databaseName", mandatory = false, help = "The database name to use") final String databaseName,
		@CliOption(key = "userName", mandatory = false, help = "The username to use") final String userName,
		@CliOption(key = "password", mandatory = false, help = "The password to use") final String password,
		@CliOption(key = "transactionManager", mandatory = false, help = "The transaction manager name") final String transactionManager,
		@CliOption(key = "persistenceUnit", mandatory = false, help = "The persistence unit name to be used in the persistence.xml file") final String persistenceUnit) {

		installJpa(ormProvider, jdbcDatabase, applicationId, jndi, hostName, databaseName, userName, password, transactionManager, persistenceUnit);
	}

	@CliCommand(value = "database properties list", help = "Shows database configuration details")
	public SortedSet<String> databaseProperties() {
		return jpaOperations.getDatabaseProperties();
	}

	@CliCommand(value = "database properties set", help = "Changes a particular database property")
	public void databaseSet(
		@CliOption(key = "key", mandatory = true, help = "The property key that should be changed") final String key,
		@CliOption(key = "value", mandatory = true, help = "The new vale for this property key") final String value) {

		propFileOperations.changeProperty(Path.SPRING_CONFIG_ROOT.contextualize(pomManagementService.getFocusedModuleName()), "database.properties", key, value);
	}

	@CliCommand(value = "database properties remove", help = "Removes a particular database property")
	public void databaseRemove(
		@CliOption(key = { "", "key" }, mandatory = true, help = "The property key that should be removed") final String key) {

		propFileOperations.removeProperty(Path.SPRING_CONFIG_ROOT.contextualize(pomManagementService.getFocusedModuleName()), "database.properties", key);
	}

	@CliAvailabilityIndicator("database properties list")
	public boolean isJpaInstalled() {
		return jpaOperations.isJpaInstalled();
	}

	@CliAvailabilityIndicator({ "database properties remove", "database properties set" })
	public boolean hasDatabaseProperties() {
		return isJpaInstalled() && jpaOperations.hasDatabaseProperties();
	}

	private boolean isJdk6OrHigher() {
		String ver = System.getProperty("java.version");
		return ver.indexOf("1.6.") > -1 || ver.indexOf("1.7.") > -1;
	}
}